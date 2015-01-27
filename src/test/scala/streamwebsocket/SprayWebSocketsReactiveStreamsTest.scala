package streamwebsocket

import akka.actor._
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._
import spray.can.websocket.frame.TextFrame
import streamwebsocket.WebSocketMessage.{Connection, Bound}

class SprayWebSocketsReactiveStreamsTest extends TestKit(ActorSystem("Websockets"))
         with FlatSpecLike with Matchers{
   implicit val materializer = FlowMaterializer()
   implicit val exec = system.dispatcher
   implicit val timeout = Timeout(3.seconds)

   "The websocket" should "do" in {
      val probe = TestProbe()


      def runClient() = {
         val client = system.actorOf(WebSocketClient.props(), "websocket-client")
         (client ? WebSocketMessage.Connect("localhost", 8080, "/")).onSuccess {
            case WebSocketMessage.Connection(inbound, outbound) =>
               println("just got the Connection")
               Source(inbound).foreach { case TextFrame(text) =>
                  val str = text.utf8String
                  println(s"client received: $str")
                  probe.ref ! s"client received: $str"
                  TextFrame("server message")
               }
               Source(200 milli, 200 milli, () => TextFrame("client message"))
                 .runWith(Sink(outbound))
         }
      }


      val server = system.actorOf(WebSocketServer.props(), "websocket")

      (server ? WebSocketMessage.Bind("localhost", 8080)).onSuccess {
         case Bound(addr, connections) =>
            runClient()
            Source(connections).foreach { case Connection(inbound, outbound) =>
               println("just got the register")
               Source(inbound).map { case TextFrame(text) =>
                  val str = text.utf8String
                  println(s"server received: $str")
                  probe.ref ! s"server received: $str"
                  TextFrame("server message")
               }.runWith(Sink(outbound))
            }
      }

      try {
         probe.expectMsg("server received: client message")
         probe.expectMsg("client received: server message")
      } finally {
         TestKit.shutdownActorSystem(system)
      }
   }
}