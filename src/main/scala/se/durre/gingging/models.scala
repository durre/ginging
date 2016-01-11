package se.durre.gingging

import akka.util.ByteString
import com.rabbitmq.client.Channel

case class RabbitMessage(
  deliveryTag: Long,
  body: ByteString,
  channel: Channel) {

  def ack(): Unit = channel.basicAck(deliveryTag, false)
  def nack(): Unit = channel.basicNack(deliveryTag, false, true)
}

case class Exchange(name: String, exchangeType: ExchangeType, durable: Boolean)
case class Queue(name: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean)
case class Bind(queue: Queue, exchange: Exchange, routingKey: String)

sealed trait ExchangeType
object ExchangeType {
  case object Direct extends ExchangeType { override def toString = "direct" }
  case object Topic extends ExchangeType { override def toString = "topic" }
  case object Fanout extends ExchangeType { override def toString = "fanout" }
  case object Headers extends ExchangeType { override def toString = "headers" }
}
