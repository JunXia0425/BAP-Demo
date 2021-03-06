package com.xiajun.bap.release.etl.dw

import com.xiajun.bap.release.constant.ReleaseConstant
import com.xiajun.bap.release.enums.ReleaseStatusEnum
import com.xiajun.bap.release.util.SparkHelper
import org.apache.spark.SparkConf
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

/**
  * DW 用户点击
  */
class DWReleaseClick {

}

object DWReleaseClick {
    private val logger: Logger = LoggerFactory.getLogger(DWReleaseClick.getClass)

    /**
      * 用户点击
      */

    def handleReleaseJob(spark: SparkSession, appName: String, bdp_day: String): Unit = {
        val begin = System.currentTimeMillis()
        try {
            import org.apache.spark.sql.functions._
            val svaeMode = SaveMode.Overwrite
            val storageLevel = ReleaseConstant.DEF_STORAGE_LEVEL

            val clickColumns = DWRealseColumnsHelper.selectDWReleaseClickColumns()
            val clickCondition = (col(ReleaseConstant.DEF_PARTITION) === lit(bdp_day)
                and
                col(ReleaseConstant.COL_RELEASE_SESSION_STATUS) === lit(ReleaseStatusEnum.CLICK.getCode))

            val clickDF = SparkHelper.readTableData(spark, ReleaseConstant.ODS_RELEASE_SESSION, clickColumns)
                .where(clickCondition)
                .repartition(ReleaseConstant.DEF_SOURCE_PARTITION)

            SparkHelper.writeTableData(clickDF, ReleaseConstant.DW_RELEASE_CLICK, svaeMode)
        } catch {
            case exception: Exception => logger.error(exception.getMessage, exception)
        } finally {
            //任务处理时长
            val msg = s"任务 ${appName}-bdp_day = ${bdp_day} 处理时长: ${System.currentTimeMillis() - begin}ms"
            logger.info(msg)
        }
    }

    def handleJobs(appName: String, bdp_day_begin: String, bdp_day_end: String): Unit = {
        var sparkSession: SparkSession = null
        try {
            val conf = new SparkConf()
                .set("hive.exec.dynamic.partition", "true")
                .set("hive.exec.dynamic.partition.mode", "nonstrict")
                .set("spark.sql.shuffle.partitions", "32")
                .set("hive.merge.mapfiles", "true")
                .set("hive.input.format", "org.apache.hadoop.hive.ql.io.CombineHiveInputFormat")
                .set("spark.sql.autoBroadcastJoinThreshold", "50485760")
                .set("spark.sql.crossJoin.enabled", "true")

            //获得sparksession
            sparkSession = SparkHelper.createSparkSession(conf)

            val dateRange: mutable.Seq[String] = SparkHelper.rangDates(bdp_day_begin, bdp_day_end)
            for (elem <- dateRange) {
                handleReleaseJob(sparkSession, appName, elem)
            }
        } catch {
            case exception: Exception => logger.error(exception.getMessage, exception)
        }

    }

    def main(args: Array[String]): Unit = {
        val appName = "dw_release_click"
        val bdp_day_begin = "2019-09-24"
        val bdp_day_end = "2019-09-24"

        handleJobs(appName, bdp_day_begin, bdp_day_end)
    }
}
