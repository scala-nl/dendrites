/** Copyright 2016 Gary Struthers

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.gs.kafka.stream

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.{ActorAttributes, Attributes, Inlet, SinkShape, Supervision}
import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.scaladsl.Sink
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic, InHandler}
import org.apache.kafka.clients.producer.{Callback, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.{CorruptRecordException, // Retriable exceptions
  InvalidMetadataException,
  NotEnoughReplicasAfterAppendException,
  NotEnoughReplicasException,
  OffsetOutOfRangeException,
  TimeoutException,
  UnknownTopicOrPartitionException,
  RetriableException}
import org.apache.kafka.common.errors.{InvalidTopicException, //Stopping exceptions
  OffsetMetadataTooLarge,
  RecordBatchTooLargeException,
  RecordTooLargeException,
  UnknownServerException}
import scala.util.control.NonFatal
import org.gs.kafka.ProducerConfig

/** Sink stage that writes to Kafka
  *
  * KafkaSink is initialized with a wrapped KafkaProducer. It includes topic, key, and Key, Value
  * types specific to the topic. KafkaProducer is heavy weight, multi-threaded, usually serves other
  * topics and is long lived. If a Kafka RetryableException is thrown while writing, KafkaSink
  * catches it and retries the write. If a write throws a subclass of KafkaException this
  * test's Decider stops the write stream.
  *
  * KafkaProducer's send() returns a Java/Guava ListenableFuture, not a nice Scala Future. Kafka's
  * ListenableFuture passes 2 arguments to a Kafka Producer Callback, a RecordMetadata and an
  * Exception. One of these arguments will be null. I cope with this in the least awful way I found.
  * If a RecordMetadata was returned it means success so an AsynCallback is called that pulls a
  * record from upstream. If an exception was returned a different AsynCallback is called that
  * deals with 2 types of exceptions, RetriableException and KafkaException.
  *
  * RetriableException is when there was a fleeting error that may not recur so resend the message.
  * This will keep retrying indefinitely until stream or other container times out.
  *
  * A KafkaException is logged and rethrown.
  *
  * @tparam K Kafka ProducerRecord key
  * @tparam V Type of serialized object received from stream and Kafka ProducerRecord value
  * @param wProd extends KafkaProducer with key, value, and topic fields
  *
  * @author Gary Struthers
  *	exception - The exception thrown during processing of this record. Null if no error occurred.
  * Possible thrown exceptions include: Non-Retriable exceptions (fatal, the message will never be
  * sent):
  */
class KafkaSink[K, V](wProd: ProducerConfig[K, V])(implicit logger: LoggingAdapter)
    extends GraphStage[SinkShape[V]] {

  val producer = wProd.producer
  val in = Inlet[V](s"KafkaSink")
  override val shape: SinkShape[V] = SinkShape(in)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {

      private def decider = inheritedAttributes.get[SupervisionStrategy].map(_.decider).
          getOrElse(Supervision.stoppingDecider)

      /** pull initializes stream requests */
      override def preStart(): Unit = {
        pull(in)
      }

      /** exception handler for producer send's callback */
      def asyncExceptions(pRecord: ProducerRecord[K, V], callback: Callback)(e: Exception): Unit = {
        e match {
          case NonFatal(e) => decider(e) match {
              case Supervision.Stop => {
                failStage(e)
              }
              case Supervision.Resume => {
                logger debug("Kafka send retryable exception, attempt retry {}", e.getMessage)
                producer send(pRecord, callback)
              }
          }
        }
      }

      /** send ProducerRecord to Kafka with callback, on success pull from upstream, on failure
        * retry on RetryableException, log and throw other exceptions
        */
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val item = grab(in)
          val producerRecord = new ProducerRecord[K, V](wProd.topic, wProd.key, item)
          var errorCallback: AsyncCallback[Exception] = null
          val pullCallback = getAsyncCallback{ (_: Unit) => pull(in) }
          val kafkaCallback = new Callback() {
            def onCompletion(meta: RecordMetadata, e: Exception): Unit = {
              if (e != null) errorCallback invoke(e) else pullCallback invoke((): Unit)
            }
          }
          val curriedAsyncEx = asyncExceptions(producerRecord, kafkaCallback) _
          errorCallback = getAsyncCallback(curriedAsyncEx)
          producer send(producerRecord, kafkaCallback)
        }
      })
    }
  }
}

/** Factory for KafkaSink with wrapped KafkaProducer and its properties configuration
  * Sink.fromGraph promotes KafkaSink from a SinkShape to a Sink
  *
  * KafkaSink companion object defines a Supervision Decider. Custom AkkaStream stages can use Akka
  * Supervision but they must provide customized ways to handle exception directives returned by the
  * Decider.
  *
  * Kafka's Retriable exceptions thrown by Kafka Producer are mapped to Supervision.Resume.
  * AkkaStream doesn't have a Retry mode, so Resume is used instead.A Producer.send that failed with
  * a Retriable exception will retry the send and will keep retrying until there is a Stop exception
  * or the stream times out.
  *
  * Other exceptions thrown by Kafka Producer are mapped to Supervision.Stop. This stops KafkaSink.
  *
  */
object KafkaSink {

  /** Create Kafka Sink as Akka Sink with Supervision
    *
    * @tparam K key type
    * @tparam V value type
  	* @param producer configuration object
 		* @param implicit logger
  	* @return Sink[V, NotUsed]
  	*/
  def apply[K, V](producer: ProducerConfig[K, V])(implicit logger: LoggingAdapter):
          Sink[V, NotUsed] = {
    val sink = Sink.fromGraph(new KafkaSink[K, V](producer))
    sink.withAttributes(ActorAttributes.supervisionStrategy(decider))
  }

  /** Supervision strategy
   	*
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/InvalidTopicException.html InvalidTopicException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/OffsetMetadataTooLarge.html OffsetMetadataTooLarge]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/RecordBatchTooLargeException.html RecordBatchTooLargeException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/RecordTooLargeException.html RecordTooLargeException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/UnknownServerException.html UnknownServerException]]
  	* Retriable exceptions (transient, may be covered by increasing #.retries):
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/CorruptRecordException.html CorruptRecordException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/InvalidMetadataException.html InvalidMetadataException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/NotEnoughReplicasAfterAppendException.html NotEnoughReplicasAfterAppendException]]
  	* NotEnoughReplicasAfterAppendException @note retries cause duplicates
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/NotEnoughReplicasException.html NotEnoughReplicasException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/OffsetOutOfRangeException.html OffsetOutOfRangeException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/TimeoutException.html TimeoutException]]
  	* @see [[http://kafka.apache.org/0100/javadoc/org/apache/kafka/common/errors/UnknownTopicOrPartitionException.html UnknownTopicOrPartitionException]]
		*/
  def decider: Supervision.Decider = {
    case _: CorruptRecordException => Supervision.Resume
    case _: UnknownServerException => Supervision.Stop // subclass of InvalidMetadataException
    case _: UnknownTopicOrPartitionException => Supervision.Resume // subclass of InvalidMetadataException
    case _: InvalidMetadataException => Supervision.Resume
    case _: NotEnoughReplicasAfterAppendException => Supervision.Resume
    case _: NotEnoughReplicasException => Supervision.Resume
    case _: OffsetOutOfRangeException => Supervision.Resume
    case _: TimeoutException => Supervision.Resume
    case _: RetriableException => Supervision.Resume
    case _: InvalidTopicException => Supervision.Stop
    case _: OffsetMetadataTooLarge => Supervision.Stop
    case _: RecordBatchTooLargeException => Supervision.Stop
    case _: RecordTooLargeException => Supervision.Stop
    case _: KafkaException => Supervision.Stop // Catch all for Kafka exceptions
    case _  => Supervision.Stop
  }
}
