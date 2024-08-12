package com.jmx.util

import com.mchange.v2.c3p0.ComboPooledDataSource
import org.apache.commons.dbutils.QueryRunner

object JDBCUtil {
  // 使用 ComboPooledDataSource 配置连接池
  val dataSource = new ComboPooledDataSource()

  // 数据库连接配置
  private val user = "root"
  private val password = "123456"
  private val url = "jdbc:mysql://localhost:3306/mydb"

  // 初始化数据源
  dataSource.setUser(user)
  dataSource.setPassword(password)
  dataSource.setDriverClass("com.mysql.cj.jdbc.Driver") // 使用更新的MySQL驱动
  dataSource.setJdbcUrl(url)
  dataSource.setAutoCommitOnClose(false) // 关闭连接时不自动提交

  /**
   * 获取 QueryRunner 实例，用于执行SQL查询。
   * @return 包含 QueryRunner 的 Option 对象，若出错则返回 None。
   */
  def getQueryRunner(): Option[QueryRunner] = {
    try {
      // 成功创建 QueryRunner 对象
      Some(new QueryRunner(dataSource))
    } catch {
      case e: Exception =>
        // 捕获异常并打印堆栈跟踪信息，返回 None
        e.printStackTrace()
        None
    }
  }
}
