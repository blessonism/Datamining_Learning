package com.jmx.metrics

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.ml.recommendation.ALS
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.commons.dbutils.QueryRunner
import com.jmx.util.JDBCUtil

// 样例类，用于保存推荐结果
case class UserMovieRecommendation(userId: Int, movieId: Int, predictedRating: Double)

object MovieRecommendationSystem {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder
      .appName("Movie Recommendation System").master("local[4]")
      .getOrCreate()

    import spark.implicits._

    // 1. 加载数据
    val ratings = spark.read.option("header", "true").csv("D:\\Desktop\\数据挖掘\\Datamining_Learning\\基于 SparkSQL 的电影分析项目实战\\ml-25m\\ratings.csv")
      .select(col("userId").cast("int"), col("movieId").cast("int"), col("rating").cast("double"))

    // 2. 划分训练集和测试集
    val Array(training, test) = ratings.randomSplit(Array(0.8, 0.2))

    // 3. 使用 ALS 算法构建模型
    val als = new ALS()
      .setMaxIter(10)
      .setRegParam(0.1)
      .setUserCol("userId")
      .setItemCol("movieId")
      .setRatingCol("rating")

    val model = als.fit(training)

    // 4. 模型评估
    val predictions = model.transform(test)
    val evaluator = new RegressionEvaluator()
      .setMetricName("rmse")
      .setLabelCol("rating")
      .setPredictionCol("prediction")

    val rmse = evaluator.evaluate(predictions)
    println(s"Root-mean-square error = $rmse")

    // 5. 生成所有用户和电影的组合
    val users = ratings.select("userId").distinct()
    val movies = ratings.select("movieId").distinct()

    val userMoviePairs = users.crossJoin(movies)

    // 6. 进行预测
    val userRecs = model.transform(userMoviePairs)
      .filter($"prediction".isNotNull) // 过滤掉无法预测的记录
      .withColumn("rank", row_number().over(Window.partitionBy("userId").orderBy($"prediction".desc)))
      .filter($"rank" <= 10) // 保留每个用户的前 10 个推荐

    // 7. 提取推荐
    val top10Recs = userRecs
      .select($"userId", $"movieId", $"prediction".alias("predictedRating"))
      .as[UserMovieRecommendation]

    // 8. 将推荐结果写入MySQL
    top10Recs.foreachPartition { partition =>
      partition.foreach(insert2Mysql)
    }

    // 关闭Spark会话
    spark.stop()
  }

  /**
   * 插入数据到MySQL中
   *
   * @param res UserMovieRecommendation 实例
   */
  private def insert2Mysql(res: UserMovieRecommendation): Unit = {
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
   * @param r UserMovieRecommendation 实例
   * @param conn QueryRunner 实例
   */
  private def upsert(r: UserMovieRecommendation, conn: QueryRunner): Unit = {
    val sql =
      """
        |REPLACE INTO user_movie_recommendations(
        |  userId,
        |  movieId,
        |  predictedRating
        |)
        |VALUES
        |  (?, ?, ?)
    """.stripMargin

    try {
      // 将基本类型转换为引用类型（如 Int -> Integer, Double -> java.lang.Double）
      conn.update(sql, new Integer(r.userId), new Integer(r.movieId), new java.lang.Double(r.predictedRating))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.exit(-1)
    }
  }

}
