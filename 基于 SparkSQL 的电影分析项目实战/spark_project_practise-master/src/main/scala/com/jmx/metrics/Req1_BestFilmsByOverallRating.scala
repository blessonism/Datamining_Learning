package com.jmx.metrics

import com.jmx.demos.tenGreatestMoviesByAverageRating
import com.jmx.util.JDBCUtil
import org.apache.commons.dbutils.QueryRunner
import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession}

/**
 * 需求1：查找电影评分个数超过5000,且平均评分较高的前十部电影名称及其对应的平均评分
 */
class Req1_BestFilmsByOverallRating extends Serializable {

  def run(moviesDataset: DataFrame, ratingsDataset: DataFrame, spark: SparkSession): Unit = {
    import spark.implicits._

    // 将moviesDataset注册成表
    moviesDataset.createOrReplaceTempView("movies")
    // 将ratingsDataset注册成表
    ratingsDataset.createOrReplaceTempView("ratings")

    // 查询SQL语句
    val ressql1 =
      """
        |WITH ratings_filter_cnt AS (
        |  SELECT
        |    movieId,
        |    COUNT(*) AS rating_cnt,
        |    AVG(rating) AS avg_rating
        |  FROM
        |    ratings
        |  GROUP BY
        |    movieId
        |  HAVING
        |    COUNT(*) >= 5000
        |),
        |ratings_filter_score AS (
        |  SELECT
        |    movieId,
        |    avg_rating
        |  FROM
        |    ratings_filter_cnt
        |  ORDER BY
        |    avg_rating DESC
        |  LIMIT 10
        |)
        |SELECT
        |  m.movieId,
        |  m.title,
        |  r.avg_rating AS avgRating
        |FROM
        |  ratings_filter_score r
        |JOIN
        |  movies m
        |ON
        |  m.movieId = r.movieId
      """.stripMargin

    // 执行SQL查询并转换为指定的DataSet类型
    val resultDS = spark.sql(ressql1).as[tenGreatestMoviesByAverageRating]

    // 打印结果，用于调试
    resultDS.show(10)
    resultDS.printSchema()

    // 将结果写入MySQL
    resultDS.foreachPartition { partition =>
      partition.foreach(insert2Mysql)
    }
  }

  /**
   * 插入数据到MySQL中
   *
   * @param res tenGreatestMoviesByAverageRating 实例
   */
  private def insert2Mysql(res: tenGreatestMoviesByAverageRating): Unit = {
    val conn = JDBCUtil.getQueryRunner()

    conn match {
      case Some(connection) =>
        upsert(res, connection)
      case None =>
        println("MySQL连接失败")
        System.exit(-1)
    }
  }

  /**
   * 将数据插入到MySQL中，使用 REPLACE INTO 来确保数据存在时进行更新，不存在时进行插入
   *
   * @param r tenGreatestMoviesByAverageRating 实例
   * @param conn QueryRunner 实例
   */
  private def upsert(r: tenGreatestMoviesByAverageRating, conn: QueryRunner): Unit = {
    val sql =
      """
        |REPLACE INTO ten_movies_averagerating(
        |  movieId,
        |  title,
        |  avgRating
        |)
        |VALUES
        |  (?, ?, ?)
      """.stripMargin

    try {
      // 执行insert操作
      conn.update(sql, r.movieId, r.title, r.avgRating)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.exit(-1)
    }
  }
}
