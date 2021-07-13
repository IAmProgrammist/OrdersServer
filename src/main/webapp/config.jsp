<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*, java.text.*, java.sql.Connection, java.sql.ResultSet, java.sql.SQLException, pvapersonal.ru.other.MySQLConnector" %>
<html>
  <head>
      <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
      <title>Администрирование пользователей</title>
  </head>
  <body>
  <h1>Пользователи:</h1>
  <table border="1">
  <tr class="bold">
  <td>ID</td>
  <td>Имя</td>
  <td onClick="setPasswordEnabled(this)">Фамилия</td>
  <td>Отчество</td>
  <td>Пароль (нажмите на пароль, чтобы отобразить его)</td>
  <td>Номер телефона</td>
  <td>Администратор</td>
  </tr>
  <%
   MySQLConnector connector = new MySQLConnector();
           try (Connection conn = connector.nonStaticGetMySQLConnection()) {
               String query = "SELECT * FROM users";
               ResultSet set = conn.createStatement().executeQuery(query);
               while (set.next()) {
                   System.out.println("<tr>");
                   System.out.println(String.format("<td>%d</td>", set.getInt("id")));
                   System.out.println(String.format("<td>%s</td>", set.getString("name")));
                   System.out.println(String.format("<td>%s</td>", set.getString("surname")));
                   System.out.println(String.format("<td>%s</td>", set.getString("middlename")));
                   System.out.println(String.format("<td onClick='setPasswordEnabled(this)'><span class='password'>%s</span> <span class='dots'>••••••</span></td>", set.getString("password")));
                   System.out.println(String.format("<td>%s</td>", set.getString("telNumber")));
                   System.out.println("<td><input type='checkbox' name='isAdmin'></td>");
               }
           }
   %>
  </table>
  </body>
</html>