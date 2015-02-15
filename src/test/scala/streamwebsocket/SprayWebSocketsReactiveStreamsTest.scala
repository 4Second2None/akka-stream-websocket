package streamwebsocket

import akka.actor._
import akka.pattern.ask
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._
import spray.can.websocket.frame.TextFrame
import streamwebsocket.WebSocketMessage.{Connection, Bound}

class SprayWebSocketsReactiveStreamsTest extends TestKit(ActorSystem("WebSockets"))
         with FlatSpecLike with Matchers{
   implicit val materializer = ActorFlowMaterializer()
   implicit val exec = system.dispatcher
   implicit val timeout = Timeout(3.seconds)
   val port = 12345
   "The WebSocket client" should "send a message to server and receive it back" in {
      val probe = TestProbe()

      val server = system.actorOf(WebSocketServer.props(), "websocket-server")

      (server ? WebSocketMessage.Bind("localhost", port)).onSuccess {
         case Bound(addr, connections) =>

            Source(connections).runForeach { case Connection(inbound, outbound) =>
               Source(inbound).map { case TextFrame(text) =>
                  val str = text.utf8String
                  println(s"server received: $str")
                  probe.ref ! s"server received: $str"
                  TextFrame("server message")
               }.runWith(Sink(outbound))
            }

            connectClient(probe)
      }



      try {
         probe.expectMsg("server received: client message")
         probe.expectMsg("client received: server message")
      } finally {
         TestKit.shutdownActorSystem(system)
      }
   }

   def connectClient(probe:TestProbe) = {
      val client = system.actorOf(WebSocketClient.props(), "websocket-client")
      (client ? WebSocketMessage.Connect("localhost", port, "/")).onSuccess {
         case WebSocketMessage.Connection(inbound, outbound) =>
            println("just got the Connection")
            Source(inbound).runForeach { case TextFrame(text) =>
               val str = text.utf8String
               println(s"client received: $str")
               probe.ref ! s"client received: $str"
               TextFrame("server message")
            }
          //./  Source(List(TextFrame("client message"))).runWith(Sink(outbound))
            Source(200 milli, 200 milli, TextFrame("client message"))
              .runWith(Sink(outbound))
      }
   }



}