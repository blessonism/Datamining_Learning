package com.jmx.metrics

import com.jmx.demos.topGenresByAverageRating
import com.jmx.util.JDBCUtil
import org.apache.commons.dbutils.QueryRunner
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
  * 需求2：查找每个电影类别及其对应的平均评分
  */
class Req2_GenresByAverageRating extends Serializable {

  def run(moviesDataset: DataFrame, ratingsDataset: DataFrame, spark: SparkSession): Unit = {
    import spark.implicits._

    // 注册 DataFrame 为临时表
    moviesDataset.createOrReplaceTempView("movies")
    ratingsDataset.createOrReplaceTempView("ratings")

    // SQL 查询语句，查找每个电影类别及其对应的平均评分
    val ressql2 =
      """
        |WITH explode_movies AS (
        |  SELECT
        |    movieId,
        |    title,
        |    category
        |  FROM
        |    movies
        |  LATERAL VIEW explode (split (genres, "\\|")) temp AS category
        |)
        |SELECT
        |  m.category AS genres,
        |  AVG(r.rating) AS avgRating
        |FROM
        |  explode_movies m
        |JOIN
        |  ratings r
        |ON
        |  m.movieId = r.movieId
        |GROUP BY
        |  m.category
      """.stripMargin

    // 执行 SQL 并转换为指定的 DataSet 类型
    val resultDS = spark.sql(ressql2).as[topGenresByAverageRating]

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
   * @param res topGenresByAverageRating 实例
   */
  private def insert2Mysql(res: topGenresByAverageRating): Unit = {
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
   * @param r topGenresByAverageRating 实例
   * @param conn QueryRunner 实例
   */
  private def upsert(r: topGenresByAverageRating, conn: QueryRunner): Unit = {
    val sql =
      """
        |REPLACE INTO genres_average_rating(
        |  genres,
        |  avgRating
        |)
        |VALUES
        |  (?, ?)
      """.stripMargin

    try {
      // 执行插入或更新操作
      conn.update(sql, r.genres, r.avgRating)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.exit(-1)
    }
  }
}