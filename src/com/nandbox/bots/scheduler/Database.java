package com.nandbox.bots.scheduler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
public class Database {
	
	public Connection connection = null;
	

	public Database(String dbName) throws SQLException
	{
		Connection connection = DriverManager.getConnection("jdbc:sqlite:"+dbName+".db");
		this.connection = connection;
	}
	
	public void createTable() throws SQLException
	{
		String sql = "CREATE TABLE IF NOT EXISTS alerts (\n"
				+ "chatId varchar(255),\n"
				+ "adminId varchar(255) NOT NULL,\n"
				+ "messageId varchar(255) NOT NULL,\n"
				+ "date varchar(255) NOT NULL,\n"
				+ "message varchar(255) NOT NULL,\n"
				+ "PRIMARY KEY(messageId)\n"
				+ ");";
		Statement s = this.connection.createStatement();
		s.execute(sql);
	}
	
	
	
	public void insertAlert(String chatId,String adminId,String messageId,String date,String message) throws SQLException
	{
		String sql = "insert into alerts values (?,?,?,?,?)";
		
		PreparedStatement pstmt = this.connection.prepareStatement(sql);
        pstmt.setString(1, chatId);
        pstmt.setString(2, adminId);
        pstmt.setString(3, messageId);
        pstmt.setString(4, date);
        pstmt.setString(5, message);
        pstmt.executeUpdate();
	}
	
	public void deleteAlert(String messageId) throws SQLException
	{
		 String sql = "DELETE FROM alerts WHERE messageId = ?";
		
		PreparedStatement pstmt = this.connection.prepareStatement(sql);
        pstmt.setString(1, messageId);
        pstmt.executeUpdate();
	}
	
	public  ArrayList<ArrayList<String>> getAlertsByAdmin(String chatId,String adminId) throws SQLException
	{
		String sql = "Select message,messageId,date from alerts where chatId = ? and adminId = ?";
		PreparedStatement pstmt = this.connection.prepareStatement(sql);
		pstmt.setString(1, chatId);
        pstmt.setString(2, adminId);
        
        ResultSet rs = pstmt.executeQuery();
        ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();
       
       while (rs.next()) {
    	   ArrayList<String> currentAlert = new ArrayList<String>();
    	   currentAlert.add(rs.getString("message"));
    	   currentAlert.add(rs.getString("messageId"));
    	   currentAlert.add(rs.getString("date"));
    	   result.add(currentAlert);
       }
       return result;
	}
	
	public boolean alertExists(String messageId) throws SQLException
	{
		String sql = "Select * from alerts where messageId = ?";
		PreparedStatement pstmt = this.connection.prepareStatement(sql);
		pstmt.setString(1, messageId);
        ResultSet rs = pstmt.executeQuery();

        if(rs.next())
        {
        	return true;
        }
        return false;
	}
	
	public void createTimeZoneTable() throws SQLException {
		String query = "create table if not exists timezone (\n"
				+ "chatId varchar(255),\n"
				+ "timeZoneHr INT NOT NULL,\n"
				+ "timeZoneMn INT NOT NULL,\n"
				+ "timeZoneOp char NOT NULL,\n"
				+ "PRIMARY KEY(chatId)\n"
				+ ");";
		
		this.connection.createStatement().execute(query);
		
	}
	
	public void insertTimeZone(String chatId, int hour, int minute, char operator) throws SQLException {
		String query = "insert into timezone values (?, ?, ?, ?);";		
		PreparedStatement pstmt = this.connection.prepareStatement(query);
		pstmt.setString(1, chatId);
		pstmt.setInt(2, hour);
		pstmt.setInt(3, minute);
		pstmt.setString(4, String.valueOf(operator));
		pstmt.executeUpdate();
	}
	
	public boolean chatIdExistsInTimeZoneTable(String chatId) throws SQLException {
		String query = "select * from timezone where chatId = ?";
		PreparedStatement pstmt = this.connection.prepareStatement(query);
		pstmt.setString(1, chatId);
        ResultSet rs = pstmt.executeQuery();
        if(rs.next())
        	return true;
        return false;
	}
	
	public void updateTimeZoneForSpecificChatId(String chatId, int hour, int minute, char operator) throws SQLException {
		String query = "update timezone set timeZoneHr = ?, timeZoneMn = ?, timeZoneOp = ? where chatId = ?;";
		PreparedStatement pstmt = this.connection.prepareStatement(query);
		pstmt.setInt(1, hour);
		pstmt.setInt(2, minute);
		pstmt.setString(3, String.valueOf(operator));
		pstmt.setString(4, chatId);
		pstmt.executeUpdate();
	}
	
	public TimeZoneOffset getTimeZoneFromDB(String chatId) throws SQLException {
		String query = "select timeZoneHr, timeZoneMn, timeZoneOp from timezone where chatId = ?;";
		PreparedStatement pstmt = this.connection.prepareStatement(query);
		pstmt.setString(1, chatId);
        ResultSet rs = pstmt.executeQuery();
        TimeZoneOffset offset = new TimeZoneOffset();
        if(rs.next()){
        	offset.setHour(rs.getInt(1));
        	offset.setMinute(rs.getInt(2));
        	offset.setOperator(rs.getString(3).charAt(0));
        }
        
        System.out.println("hour: " + offset.getHour() + " minute: " + offset.getMinute());
        
        return offset;

	}
}
