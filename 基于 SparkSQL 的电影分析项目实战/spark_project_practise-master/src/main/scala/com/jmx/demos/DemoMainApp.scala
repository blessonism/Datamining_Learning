package com.jmx.demos

import com.jmx.metrics.{Req1_BestFilmsByOverallRating, Req2_GenresByAverageRating, Req3_MostRatedFilms}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType

object DemoMainApp {
  // 文件路径(定义CSV文件路径为常量，便于后续维护)
  private val MOVIES_CSV_FILE_PATH = "D:\\Desktop\\数据挖掘\\Datamining_Learning\\基于 SparkSQL 的电影分析项目实战\\ml-25m\\movies.csv"
  private val RATINGS_CSV_FILE_PATH = "D:\\Desktop\\数据挖掘\\Datamining_Learning\\基于 SparkSQL 的电影分析项目实战\\ml-25m\\ratings.csv"

  def main(args: Array[String]): Unit = {
    // 初始化SparkSession，指定使用本地模式，并设置4个线程
    val spark = SparkSession
      .builder
      .appName("Movie Analysis")  // 为Spark应用指定名称，便于监控
      .master("local[4]")
      .getOrCreate()

    // 加载自定义的Schema
    val schemaLoader = new SchemaLoader

    // 加载电影数据集
    val movieDF = readCsvIntoDataSet(spark, MOVIES_CSV_FILE_PATH, schemaLoader.getMovieSchema)

    // 读取Rating数据集
    val ratingDF = readCsvIntoDataSet(spark, RATINGS_CSV_FILE_PATH, schemaLoader.getRatingSchema)

    movieDF.printSchema()
    ratingDF.printSchema()
/*
    // 需求1：查找电影评分个数超过5000,且平均评分较高的前十部电影名称及其对应的平均评分
    val bestFilmsByOverallRating = new Req1_BestFilmsByOverallRating
    bestFilmsByOverallRating.run(movieDF, ratingDF, spark)

    // 需求2：查找每个电影类别及其对应的平均评分
    val genresByAverageRating = new Req2_GenresByAverageRating
    genresByAverageRating.run(movieDF, ratingDF, spark)

    // 需求3：查找被评分次数较多的前十部电影
    val mostRatedFilms = new Req3_MostRatedFilms
    mostRatedFilms.run(movieDF, ratingDF, spark)
    */


    // 关闭SparkSession，释放资源
    spark.close()
  }

  /**
   * 读取CSV文件并加载为DataFrame。
   *
   * @param spark SparkSession对象，用于数据处理。
   * @param path CSV文件的路径。
   * @param schema 数据集的Schema结构。
   * @return 加载的DataFrame。
   */
  def readCsvIntoDataSet(spark: SparkSession, path: String, schema: StructType) = {

    val dataSet = spark.read
      .format("csv")
      .option("header", "true")
      .schema(schema)
      .load(path)
    dataSet
  }
}
