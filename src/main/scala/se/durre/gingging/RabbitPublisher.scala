package se.durre.gingging

import com.rabbitmq.client._
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection
import com.typesafe.scalalogging.LazyLogging

class RabbitPublisher(connection: AutorecoveringConnection, exchange: Exchange, queue: Queue) extends RecoveryListener with LazyLogging {

  connection.addRecoveryListener(this)

  private val channel = connection.createChannel()
  private val undeliveredMessages = new scala.collection.mutable.Queue[String]

  def sendMsg(msg: String) = {
    if (connection.isOpen) {
      try {
        channel.basicPublish(exchange.name, queue.name, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes)
      } catch {
        case e: Exception => {
          logger.error("Error while putting message in rabbitmq queue: " + e.getMessage)
          enqueueMessage(msg)
        }
      }
    } else {
      enqueueMessage(msg)
    }
  }

  private def enqueueMessage(msg: String): Unit = {
    logger.info(s"No connection available. Putting message in queue. Queue size: (${undeliveredMessages.size})")
    undeliveredMessages.enqueue(msg)
  }

  override def handleRecovery(recoverable: Recoverable): Unit = {
    logger.info(s"Recovered connection to RabbitMQ.")
    if (undeliveredMessages.nonEmpty) {
      logger.info(s"Will try to deliver: ${undeliveredMessages.size} messages")
      undeliveredMessages.dequeueAll(_ => true).foreach(msg => sendMsg(msg))
    }
  }
}
