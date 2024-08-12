package com.jmx.metrics

import com.jmx.demos.tenMostRatedFilms
import com.jmx.util.JDBCUtil
import org.apache.commons.dbutils.QueryRunner
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
  * 需求3：查找被评分次数较多的前十部电影.
  */
class Req3_MostRatedFilms extends Serializable {

  def run(moviesDataset: DataFrame, ratingsDataset: DataFrame, spark: SparkSession): Unit = {
    import spark.implicits._

    // 注册 DataFrame 为临时表
    moviesDataset.createOrReplaceTempView("movies")
    ratingsDataset.createOrReplaceTempView("ratings")

    // SQL 查询语句，查找评分次数最多的前十部电影
    val ressql3 =
      """
        |WITH rating_group AS (
        |  SELECT
        |    movieId,
        |    COUNT(*) AS ratingCnt
        |  FROM
        |    ratings
        |  GROUP BY
        |    movieId
        |),
        |rating_filter AS (
        |  SELECT
        |    movieId,
        |    ratingCnt
        |  FROM
        |    rating_group
        |  ORDER BY
        |    ratingCnt DESC
        |  LIMIT 10
        |)
        |SELECT
        |  m.movieId,
        |  m.title,
        |  r.ratingCnt
        |FROM
        |  rating_filter r
        |JOIN
        |  movies m
        |ON
        |  r.movieId = m.movieId
      """.stripMargin

    // 执行 SQL 并转换为指定的 DataSet 类型
    val resultDS = spark.sql(ressql3).as[tenMostRatedFilms]

    // 打印结果用于调试
    resultDS.show(10)
    resultDS.printSchema()

    // 将结果写入 MySQL
    resultDS.foreachPartition { partition =>
      partition.foreach(insert2Mysql)
    }
  }

  /**
   * 插入数据到 MySQL 中
   *
   * @param res tenMostRatedFilms 实例
   */
  private def insert2Mysql(res: tenMostRatedFilms): Unit = {
    val conn = JDBCUtil.getQueryRunner()

    conn match {
      case Some(connection) =>
        upsert(res, connection)
      case None =>
        println("MySQL 连接失败")
        System.exit(-1)
    }
  }

  /**
   * 将数据插入到 MySQL 中，使用 REPLACE INTO 来确保数据存在时进行更新，不存在时进行插入
   *
   * @param r tenMostRatedFilms 实例
   * @param conn QueryRunner 实例
   */
  private def upsert(r: tenMostRatedFilms, conn: QueryRunner): Unit = {
    val sql =
      """
        |REPLACE INTO ten_most_rated_films(
        |  movieId,
        |  title,
        |  ratingCnt
        |)
        |VALUES
        |  (?, ?, ?)
      """.stripMargin

    try {
      // 执行插入或更新操作
      conn.update(sql, r.movieId, r.title, r.ratingCnt)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.exit(-1)
    }
  }
}
