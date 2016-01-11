package se.durre.gingging

import akka.actor.ActorLogging
import akka.stream.actor.ActorPublisher
import akka.util.ByteString
import com.rabbitmq.client._

class RabbitConsumerActor(connection: Connection, queue: Queue) extends ActorPublisher[RabbitMessage] with ActorLogging {

  private val channel = connection.createChannel()
  private val consumer = new DefaultConsumer(channel) {
    override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]) =
      self ! new RabbitMessage(envelope.getDeliveryTag, ByteString(body), channel)

  }

  private def register(channel: Channel, queue: Queue, consumer: Consumer): Unit =  {
    val autoAck = false
    channel.basicConsume(queue.name, autoAck, consumer)
  }

  register(channel, queue, consumer)

  override def receive = {
    case msg: RabbitMessage =>
      if (isActive && totalDemand > 0) {
        onNext(msg)
      } else {
        msg.nack()
      }
  }
}
