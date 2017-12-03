package com.muyi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class ChatRoom extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String DIR = "E:/Programs/apache-tomcat-9.0.0.M26/webapps/ChatRoom/File";
	//private static final String DIR = "/opt/tomcat/webapps/ChatRoom/File";
	private String url = "jdbc:mysql://localhost:3306/chat_room";
	private String user = "root";
	private String pwd = "123456";
	private Connection conn = null;
	private Statement stmt = null;

	public ChatRoom() {
		super();
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setCharacterEncoding("UTF-8");
		try {
			conn = DriverManager.getConnection(url, user, pwd);
			stmt = conn.createStatement();

			switch (request.getParameter("type")) {
			case "login":
				login(request, response);
				break;
			case "register":
				register(request, response);
				break;
			case "change":
				changeStatus(request);
				break;
			case "send":
				insertMsg(request, response);
				break;
			case "request":
				requestMsg(request, response);
				break;
			case "upload":
				uploadFile(request, response);
				break;
			case "queryfile":
				queryFile(response);
				break;
			case "queryuser":
				queryUser(response);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
				if (stmt != null) {
					stmt.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 查询在线的用户
	 * 
	 * @param response
	 */
	private void queryUser(HttpServletResponse response) {

		PrintWriter out = null;

		try {
			// 返回当前在线用户
			out = response.getWriter();
			JSONArray jsonArray = new JSONArray();
			JSONObject jsonObject = new JSONObject();
			String sql = "select * from chat_room.users where status='1'";
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				jsonObject.put("username", rs.getString("username"));
				jsonArray.add(jsonObject);
				jsonObject.clear();
			}
			rs.close();
			out.print(jsonArray);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}

	}

	/**
	 * 查询已上传的文件
	 * 
	 * @param response
	 */
	private void queryFile(HttpServletResponse response) {

		PrintWriter out = null;

		try {

			out = response.getWriter();

			String sql = "select * from chat_room.file";
			ResultSet rs = stmt.executeQuery(sql);
			JSONArray jsonArray = new JSONArray();
			JSONObject jsonObject = new JSONObject();
			while (rs.next()) {
				jsonObject.put("userName", rs.getString("username"));
				jsonObject.put("fileName", rs.getString("filename"));
				jsonObject.put("filePath", rs.getString("filepath"));
				jsonObject.put("fileSize", rs.getString("filesize"));
				jsonObject.put("uploadTime", rs.getString("uploadtime"));
				jsonArray.add(jsonObject);
				jsonObject.clear();
			}
			rs.close();
			out.print(jsonArray);
		} catch (Exception e) {
			e.printStackTrace();
			out.print("fail");
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * 上传文件
	 * 
	 * @param request
	 * @param response
	 */
	private void uploadFile(HttpServletRequest request, HttpServletResponse response) {

		// 上传文件比较耗时，新开一个连接
		Connection conn1 = null;
		Statement stmt1 = null;
		PrintWriter out = null;

		int fileSize = Integer.parseInt(request.getParameter("filesize"));
		File userDir;
		File file = null;

		// 输入流
		ServletInputStream in = null;
		// 输出流
		OutputStream os = null;

		try {

			conn1 = DriverManager.getConnection(url, user, pwd);
			stmt1 = conn1.createStatement();
			out = response.getWriter();

			String fileName = URLEncoder.encode(request.getParameter("filename"), "UTF-8");
			String userName = URLEncoder.encode(request.getParameter("username"), "utf-8");

			userDir = new File(DIR, userName);
			userDir.mkdir();
			file = new File(userDir, fileName);
			
			int index = 1;
			while (file.exists()) {
				// 如果文件已存在，在文件后加字符
				StringBuffer sb = new StringBuffer(file.getName());
				sb = sb.insert(sb.lastIndexOf("."), "(" + (index ++) + ")");
				file = new File(userDir, sb.toString());
			}
			in = request.getInputStream();
			os = new FileOutputStream(file);
			byte[] buff = new byte[1024];
			int len;
			while ((len = in.read(buff)) != -1) {
				os.write(buff, 0, len);
				// 返回进度
				out.print(file.length() + "\n");
			}

			// 将文件信息存入表中
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String uploadTime = df.format(new Date());
			String filePath = "http://localhost:8080/ChatRoom/File/" + userName + "/" + file.getName();
			// userName 编码两次，当出现文件重名时，存入相同的文件名，生成的链接不同
			String sql2 = "insert into chat_room.file(username,filename,filepath,filesize,uploadtime)values('"
					+ URLEncoder.encode(userName, "utf-8") + "','" + fileName + "','" + filePath + "','" + fileSize
					+ "','" + uploadTime + "')";
			stmt1.executeUpdate(sql2);

			// 将上传文件的消息存入 msg 表中
			String sql3 = "insert into chat_room.msg(username,content)values('system','“" + userName;
			sql3 += URLEncoder.encode("”上传了文件（", "utf-8") + fileName + " )" + "')";
			stmt1.executeUpdate(sql3);

			conn1.close();
		} catch (Exception e) {
			out.print("error");
			e.printStackTrace();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
				if (os != null) {
					os.close();
				}
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 请求消息
	 * 
	 * @param response
	 */
	private void requestMsg(HttpServletRequest request, HttpServletResponse response) {

		PrintWriter out = null;

		try {
			out = response.getWriter();

			JSONArray jsonArray = new JSONArray();
			JSONObject jsonObject = new JSONObject();

			// 返回 msg 中已有记录总数
			String sql1 = "select * from chat_room.msg";
			ResultSet rs1 = stmt.executeQuery(sql1);
			rs1.last();
			jsonObject.put("index", rs1.getString("id"));
			jsonArray.add(jsonObject);
			jsonObject.clear();

			int index = Integer.parseInt(request.getParameter("index"));
			// 仅返回上次请求数据后，msg 中新增的数据
			String sql2 = "select * from chat_room.msg where id>" + index;

			Statement stmt1 = conn.createStatement();
			ResultSet rs2 = stmt1.executeQuery(sql2);
			while (rs2.next()) {

				jsonObject.put("username", rs2.getString("username"));
				jsonObject.put("content", rs2.getString("content"));
				jsonArray.add(jsonObject);
				jsonObject.clear();
			}
			stmt1.close();

			out.print(jsonArray);
		} catch (Exception e) {
			out.print("fail");
			e.printStackTrace();
		}

	}

	/**
	 * 插入消息
	 * 
	 * @param request
	 * @param response
	 */
	private void insertMsg(HttpServletRequest request, HttpServletResponse response) {

		PrintWriter out = null;

		String userName = request.getParameter("username");
		String content = request.getParameter("content");

		try {
			out = response.getWriter();

			userName = URLEncoder.encode(userName, "utf-8");
			content = URLEncoder.encode(content, "utf-8");
			// 将发送的消息存储到 msg 表中。
			String sql = "insert into chat_room.msg(username,content)values('" + userName + "','" + content + "')";
			// 存储成功返回 1，失败返回 fail
			if (stmt.executeUpdate(sql) > 0) {
				out.print("1");
			}
		} catch (Exception e) {
			out.print("fail");
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}

	}

	/**
	 * 上线 / 下线
	 * 
	 * @param request
	 */
	private void changeStatus(HttpServletRequest request) {

		String userName = request.getParameter("username");
		String status = request.getParameter("status");

		try {

			userName = URLEncoder.encode(request.getParameter("username"), "utf-8");
			String sql1;
			String sql2 = "insert into chat_room.msg(username,content)values('system','“" + userName;
			if (status.equals("online")) {
				sql1 = "update chat_room.users set status=1 where username='" + userName + "'";
				sql2 += URLEncoder.encode("”上线", "utf-8") + "')";
			} else {
				sql1 = "update chat_room.users set status=0 where username='" + userName + "'";
				sql2 += URLEncoder.encode("”下线", "utf-8") + "')";
			}

			stmt.executeUpdate(sql1);
			stmt.executeUpdate(sql2);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 注册
	 * 
	 * @param request
	 * @param response
	 */
	private void register(HttpServletRequest request, HttpServletResponse response) {
		PrintWriter out = null;
		String userName = request.getParameter("username");
		String password = request.getParameter("password");
		try {

			userName = URLEncoder.encode(request.getParameter("username"), "utf-8");
			out = response.getWriter();

			// 检查用户名是否已经存在，存在返回 2。
			String sql1 = "select * from chat_room.users where username='" + userName + "'";
			if (stmt.executeQuery(sql1).next()) {
				out.print("2");
				return;
			}

			String sql2 = "insert into chat_room.users(username,password)values('" + userName + "','" + password + "')";
			// 注册成功返回 1。
			int row = stmt.executeUpdate(sql2);
			if (row > 0) {
				out.print("1");
			}
		} catch (Exception e) {
			out.print("fail");
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/**
	 * 登录
	 * 
	 * @param request
	 * @param response
	 */
	private void login(HttpServletRequest request, HttpServletResponse response) {
		PrintWriter out = null;
		String userName = request.getParameter("username");
		String password = request.getParameter("password");
		JSONObject jsonObject = new JSONObject();
		try {

			userName = URLEncoder.encode(request.getParameter("username"), "utf-8");

			out = response.getWriter();

			// 检查用户是否存在和密码是否正确
			String sql1 = "select * from chat_room.users where username='" + userName + "' and password='" + password
					+ "'";
			ResultSet rs = stmt.executeQuery(sql1);

			if (rs.next()) {
				// 如果存在该用户并密码正确，status 返回 1，表示可以登录；
				jsonObject.put("status", "1");

				// 获取 msg 表中已有的记录数
				String sql2 = "select * from chat_room.msg";
				ResultSet rs2 = stmt.executeQuery(sql2);
				if (rs2.next()) {
					rs2.last();
					jsonObject.put("index", rs2.getString("id"));
				} else {
					jsonObject.put("index", 0);
				}

				// 检查该用户是否已经登录,已经登录 logined 返回 1，未登录返回 0；
				String sql3 = "select * from chat_room.users where username='" + userName + "' and status= '1'";
				ResultSet rs3 = stmt.executeQuery(sql3);
				if (rs3.next()) {
					jsonObject.put("logined", "1");
				} else {
					jsonObject.put("logined", "0");
				}
			} else {
				jsonObject.put("status", "0");
			}

			out.print(jsonObject);

		} catch (Exception e) {
			jsonObject.put("status", "0");
			out.print(jsonObject);
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}
