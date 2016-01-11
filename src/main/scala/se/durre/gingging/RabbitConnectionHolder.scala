package se.durre.gingging

import akka.actor.{ActorSystem, Cancellable}
import com.rabbitmq.client._
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

class RabbitConnectionHolder(connectionURL: String)(implicit system: ActorSystem) extends ShutdownListener with RecoveryListener with LazyLogging {

  private val factory = new ConnectionFactory
  private var connection: Option[AutorecoveringConnection] = None
  private var channel: Option[Channel] = None

  // Fault tolerance
  private var retrySetup: Option[Cancellable] = None
  private val retryInterval = 5000
  private var promisedConnection: Promise[AutorecoveringConnection] = null

  // Enqueue actions for when connection goes down
  private val undeclaredExchanges = new scala.collection.mutable.Queue[Exchange]
  private val undeclaredQueues = new scala.collection.mutable.Queue[Queue]
  private val failedBindings = new scala.collection.mutable.Queue[Bind]

  def createConnection(): Future[AutorecoveringConnection] = {
    promisedConnection = Promise[AutorecoveringConnection]()
    setup()

    connection match {
      case None  =>
        retrySetup = Some(system.scheduler.schedule(retryInterval.milliseconds, retryInterval.milliseconds, new Runnable {
          override def run(): Unit = setup()
        }))
      case Some(_) => // All good
    }

    promisedConnection.future
  }

  def declareExchange(exchange: Exchange): Unit = {
    channel match {
      case Some(chan) => chan.exchangeDeclare(exchange.name, exchange.exchangeType.toString, exchange.durable)
      case None =>
        undeclaredExchanges.enqueue(exchange)
        logger.error("RabbitMQ: Can't declare exchange because there was no channel open")
    }
  }

  def declareQueue(queue: Queue) = {
    channel match {
      case Some(chan) => chan.queueDeclare(queue.name, queue.durable, queue.exclusive, queue.autoDelete, null)
      case None =>
        undeclaredQueues.enqueue(queue)
        logger.error("RabbitMQ: Can't declare queue because there was no channel open")
    }
  }

  def bindQueue(bind: Bind) = {
    channel match {
      case Some(chan) => chan.queueBind(bind.queue.name, bind.exchange.name, bind.routingKey)
      case None =>
        failedBindings.enqueue(bind)
        logger.error("RabbitMQ: Can't bind exchange & queue because there was no channel open")
    }
  }

  private def setup() = {
    try {
      factory.setUri(connectionURL)
      factory.setAutomaticRecoveryEnabled(true)
      factory.setNetworkRecoveryInterval(retryInterval)
      val conn: AutorecoveringConnection = factory.newConnection().asInstanceOf[AutorecoveringConnection]
      val chan = conn.createChannel()

      // Start listening for disconnects and recoveries
      chan.addShutdownListener(this)
      conn match {
        case autoRecovery: AutorecoveringConnection => autoRecovery.addRecoveryListener(this)
      }

      connection = Some(conn)
      channel = Some(chan)
      logger.info(s"Successfully connected to RabbitMQ")

      onConnected()
    } catch {
      case e: Exception =>
        logger.error(s"Error while connecting to RabbitMQ. Reason: ${e.getMessage}")
    }
  }

  override def shutdownCompleted(e: ShutdownSignalException): Unit = {
    logger.error("Detected disconnect from RabbitMQ")
  }

  override def handleRecovery(recoverable: Recoverable): Unit = {
    logger.info(s"Recovered connection to RabbitMQ.")
    onConnected()
  }

  private def onConnected() = {
    retrySetup.map(_.cancel())

    if (!promisedConnection.isCompleted) {
      promisedConnection.success(connection.get)
    }

    // Retry all buffered actions
    if (undeclaredExchanges.nonEmpty) {
      logger.info(s"Will try to declare: ${undeclaredExchanges.size} exchanges")
      undeclaredExchanges.dequeueAll(_ => true).foreach(exchange => declareExchange(exchange))
    }

    if (undeclaredQueues.nonEmpty) {
      logger.info(s"Will try to declare: ${undeclaredQueues.size} queues")
      undeclaredQueues.dequeueAll(_ => true).foreach(queue => declareQueue(queue))
    }

    if (failedBindings.nonEmpty) {
      logger.info(s"Will try to bind: ${failedBindings.size} bindings")
      failedBindings.dequeueAll(_ => true).foreach(queue => bindQueue(queue))
    }
  }
}
