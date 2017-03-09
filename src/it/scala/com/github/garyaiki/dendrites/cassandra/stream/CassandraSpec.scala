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
package com.github.garyaiki.dendrites.cassandra.stream

import akka.actor.ActorSystem
import com.datastax.driver.core.{Cluster, ResultSet, Session}
import com.datastax.driver.core.policies.{DefaultRetryPolicy, ExponentialReconnectionPolicy, LoggingRetryPolicy}
import java.util.UUID
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.ExecutionContext
import com.github.garyaiki.dendrites.cassandra.{Playlists, PlaylistSongConfig, Songs}
import com.github.garyaiki.dendrites.cassandra.Playlists.Playlist
import com.github.garyaiki.dendrites.cassandra.Songs.Song
import com.github.garyaiki.dendrites.cassandra.{close, connect, createCluster, createLoadBalancingPolicy, createSchema,
  dropSchema, executeBoundStmt, initLoadBalancingPolicy, logMetadata, registerQueryLogger}

class CassandraSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem("dendrites")
  implicit val ec: ExecutionContext = system.dispatcher
  val myConfig = PlaylistSongConfig
  val schema = myConfig.keySpace
  var cluster: Cluster = null
  var session: Session = null
  val songsTags = Set[String]("jazz", "2013")
  val songId = UUID.randomUUID
  val song = Song(songId,"La Petite Tonkinoise","Bye Bye Blackbird","Joséphine Baker",songsTags)
  val plId = UUID.randomUUID
  val playlist = Playlist(plId,"La Petite Tonkinoise","Bye Bye Blackbird","Joséphine Baker",songId)

  override def beforeAll() {
    val addresses = myConfig.getInetAddresses()
    val retryPolicy = new LoggingRetryPolicy(DefaultRetryPolicy.INSTANCE)
    val reConnectPolicy = new ExponentialReconnectionPolicy(10L, 10L)
    cluster = createCluster(addresses, retryPolicy, reConnectPolicy)
    cluster.init()//DEBUG
    val lbp = createLoadBalancingPolicy(myConfig.localDataCenter)
    initLoadBalancingPolicy(cluster, lbp)
    logMetadata(cluster)
    registerQueryLogger(cluster)
    session = connect(cluster)
    val strategy = myConfig.replicationStrategy
    val createSchemaRS = createSchema(session, schema, strategy, 3)
  }

  "A Cassandra client" should {
    "create a Song table" in {
      val songTRS = Songs.createTable(session, schema)
      songTRS shouldBe a [ResultSet]
    }

    "create a Playlist table" in {
      val playlistTRS = Playlists.createTable(session, schema)
      playlistTRS shouldBe a [ResultSet]
    }
  }

  "insert a Song" in {
      val prepStmt = Songs.prepInsert(session, schema)
      val bndStmt = Songs.bndInsert(prepStmt, song)
      val rs = executeBoundStmt(session, bndStmt)
      rs shouldBe a [ResultSet]
  }

  "insert a Playlist" in {
      val prepStmt = Playlists.prepInsert(session, schema)
      val bndStmt = Playlists.bndInsert(prepStmt, playlist)
      val rs = executeBoundStmt(session, bndStmt)
      rs shouldBe a [ResultSet]
  }

  "query a Song" in {
      val prepStmt = Songs.prepQuery(session, schema)
      val bndStmt = Songs.bndQuery(prepStmt, song.id)
      val rs = executeBoundStmt(session, bndStmt)
      val row = rs.one()
      Songs.mapRow(row) shouldBe song
  }

  "query a Playlist" in {
      val prepStmt = Playlists.prepQuery(session, schema)
      val bndStmt = Playlists.bndQuery(prepStmt, plId)
      val rs = executeBoundStmt(session, bndStmt)
      val row = rs.one()
      Playlists.mapRow(row) shouldBe playlist
  }

  override def afterAll() {
    dropSchema(session, schema)
    close(session, cluster)
  }
}
