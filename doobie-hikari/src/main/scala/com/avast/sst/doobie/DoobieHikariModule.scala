package com.avast.sst.doobie

import java.util.Properties
import java.util.concurrent.{ScheduledExecutorService, ThreadFactory}

import cats.Show
import cats.effect.{Async, Blocker, ContextShift, Resource, Sync}
import cats.syntax.show._
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import doobie.enum.TransactionIsolation
import doobie.hikari.HikariTransactor

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object DoobieHikariModule {

  /** Makes [[doobie.hikari.HikariTransactor]] initialized with the given config.
    *
    * @param boundedConnectExecutionContext [[scala.concurrent.ExecutionContext]] used for creating connections (should be bounded!)
    */
  def make[F[_]: Async](
      config: DoobieHikariConfig,
      boundedConnectExecutionContext: ExecutionContext,
      blocker: Blocker,
      metricsTrackerFactory: Option[MetricsTrackerFactory] = None
  )(implicit cs: ContextShift[F]): Resource[F, HikariTransactor[F]] = {
    for {
      hikariConfig <- Resource.liftF(makeHikariConfig(config, metricsTrackerFactory))
      transactor <- HikariTransactor.fromHikariConfig(hikariConfig, boundedConnectExecutionContext, blocker)
    } yield transactor
  }

  implicit private val transactionIsolationShow: Show[TransactionIsolation] = {
    case TransactionIsolation.TransactionNone            => "TRANSACTION_NONE"
    case TransactionIsolation.TransactionReadUncommitted => "TRANSACTION_READ_UNCOMMITTED"
    case TransactionIsolation.TransactionReadCommitted   => "TRANSACTION_READ_COMMITTED"
    case TransactionIsolation.TransactionRepeatableRead  => "TRANSACTION_REPEATABLE_READ"
    case TransactionIsolation.TransactionSerializable    => "TRANSACTION_SERIALIZABLE"
  }

  private def makeHikariConfig[F[_]: Sync](
      config: DoobieHikariConfig,
      metricsTrackerFactory: Option[MetricsTrackerFactory],
      scheduledExecutorService: Option[ScheduledExecutorService] = None,
      threadFactory: Option[ThreadFactory] = None
  ): F[HikariConfig] = {
    Sync[F].delay {
      val c = new HikariConfig()
      c.setDriverClassName(config.driver)
      c.setJdbcUrl(config.url)
      c.setUsername(config.username)
      c.setPassword(config.password)
      c.setAutoCommit(config.autoCommit)
      c.setConnectionTimeout(config.connectionTimeout.toMillis)
      c.setIdleTimeout(config.idleTimeout.toMillis)
      c.setMaxLifetime(config.maxLifeTime.toMillis)
      c.setMinimumIdle(config.minimumIdle)
      c.setMaximumPoolSize(config.maximumPoolSize)
      c.setReadOnly(config.readOnly)
      c.setAllowPoolSuspension(config.allowPoolSuspension)
      c.setIsolateInternalQueries(config.isolateInternalQueries)
      c.setRegisterMbeans(config.registerMBeans)
      val dataSourceProperties = new Properties()
      dataSourceProperties.putAll(config.dataSourceProperties.asJava)
      c.setDataSourceProperties(dataSourceProperties)

      config.leakDetectionThreshold.map(_.toMillis).foreach(c.setLeakDetectionThreshold)
      config.initializationFailTimeout.map(_.toMillis).foreach(c.setInitializationFailTimeout)
      config.poolName.foreach(c.setPoolName)
      config.validationTimeout.map(_.toMillis).foreach(c.setValidationTimeout)
      config.transactionIsolation.map(_.show).foreach(c.setTransactionIsolation)

      scheduledExecutorService.foreach(c.setScheduledExecutor)
      threadFactory.foreach(c.setThreadFactory)

      metricsTrackerFactory.foreach(c.setMetricsTrackerFactory)
      c
    }
  }

}
