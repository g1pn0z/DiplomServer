package com.stologontegin.diplom.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class socketserver {
	
	public static final Integer SERVER_LISTENING_PORT = 8898;
	
	public static final String REQUEST_GET_USER = "GET_USER";
	public static final String REQUEST_GET_SCRIPT = "QR_CODE-BEGIN";
	public static final String RESPONSE_USER_OK = "USER_OK";
	public static final String RESPONSE_USER_FAIL = "USER_FAIL";
	public static final String RESPONSE_SCRIPT_OK = "SCRIPT_OK";
	public static final String RESPONSE_SCRIPT_FAIL = "SCRIPT_FAIL";
	
	public static final String REQUEST_GET_FILE = "GET_FILE";
	public static final String REQUEST_NO_FILE = "NO_FILE";
	
	
	public static void main(String[] args) {
		/* Work with Oracle DB */
		try {
			Class.forName("oracle.jdbc.driver.OracleDriver");
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		/* JDBC Connection */
		Connection dbConnection = null;
		PreparedStatement getScriptStatement = null;
		PreparedStatement getUserStatement = null;
		
		/* Socket Connection */		
		ServerSocket serverSocket = null;
		Socket socket = null;
		DataInputStream dataInputStream = null;
		DataOutputStream dataOutputStream = null;
		InetAddress inetAddress = null;
		  
		String inMessage = null;
		String outMessage = null;
		String qrCode = null;
		String fileName = null;
		  
		try {
			//get server ip
			inetAddress = InetAddress.getLocalHost();
			System.out.println("server ip :"+inetAddress.getHostAddress());
			//create socket
			serverSocket = new ServerSocket(SERVER_LISTENING_PORT);
			System.out.println("Listening :" + SERVER_LISTENING_PORT);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			String url = "jdbc:oracle:thin:@127.0.0.1:1521/orcl";
			String userLogin = "QR_SERVER";
			String userPassword = "123456";
			dbConnection = DriverManager.getConnection(url,userLogin,userPassword);
			getScriptStatement = dbConnection.prepareStatement("select " +
					"steps.*, content.text_content1, content.text_content2, qr_code, content.picture_blob " +
					"from " +
					"steps, step_type, content " +
					"where " +
					"steps.id = content.step__id " +
					"and steps.step_type__id = step_type.id " +
					"and steps.script__id = " +
					"(select max(id) from scripts " +
					"where qr__id = " +
					"(select max(id) from qr " +
					"where qr_code = ?)) order by steps.id asc",ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_UPDATABLE);
			getUserStatement = dbConnection.prepareStatement("select nvl(id,0) from users where login = ? and password = ?");
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		  
		
		while(true) {
			try {
				//wait for connection
				System.out.println("waiting for connection... !");
				socket = serverSocket.accept();
				System.out.println("socket" + socket);
				System.out.println("ip: " + socket.getInetAddress());
				dataInputStream = new DataInputStream(socket.getInputStream());
				dataOutputStream = new DataOutputStream(socket.getOutputStream());
				  
				//#1 Init session
				inMessage = dataInputStream.readUTF();
				System.out.println("from_client: " + inMessage);
				
				/* #1 client: QR_CODE-BEGIN
				 * #1.1 client: QR_CODE
				 * #2 server: SCRIPT_OK / SCRIPT_FAIL
				 * #2.1 server: NEXT / END_SCRIPT_SEND
				 * #3 server: STEP_TYPE
				 * #3.1 server: <step_environment>
				 * #4 server: <step_content>
				 */
				if(inMessage.intern() == REQUEST_GET_SCRIPT) {
					String stepType = null;
					
					//#1.1 Get QR_CODE
					qrCode = dataInputStream.readUTF();
					System.out.println("from_client: " + qrCode);
					
					//#2
					getScriptStatement.setString(1, qrCode);
					ResultSet resultSet = null;
					try {
						resultSet = getScriptStatement.executeQuery();
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					if(resultSet.next()) {
						outMessage = RESPONSE_SCRIPT_OK;
						dataOutputStream.writeUTF(outMessage);
						System.out.println("to_client: " + outMessage);
						resultSet.beforeFirst();
					} else {
						outMessage = RESPONSE_SCRIPT_FAIL;
						dataOutputStream.writeUTF(outMessage);
						System.out.println("to_client: " + outMessage);
					}
					
					/* #2.1 - 4
					 * process results
					 */
					while(resultSet.next()) {
						//#2.1
						outMessage = "NEXT";
						dataOutputStream.writeUTF(outMessage);
						System.out.println("to_client: " + outMessage);
						
						//#3
						stepType = resultSet.getString("step_type__id");
						dataOutputStream.writeUTF(stepType);
						System.out.println("to_client: " + stepType);
						
						//#3.1
						outMessage = resultSet.getString("step_env");
						dataOutputStream.writeUTF(outMessage);
						System.out.println("to_client: " + outMessage);
						
						//#4
						/*
						 * text_content1
						 */
						if(stepType.intern() == "1") {
							outMessage = resultSet.getString("text_content1");
							dataOutputStream.writeUTF(outMessage);
							System.out.println("to_client: " + outMessage);
						}
						/*
						 * text_content1 (aka fileName)
						 * file
						 */
						if((stepType.intern() == "2")||(stepType.intern() == "4")||(stepType.intern() == "6")) {
							fileName = resultSet.getString("text_content1");
							dataOutputStream.writeUTF(fileName);
							System.out.println("to_client: " + fileName);
							
							inMessage = dataInputStream.readUTF();
							System.out.println("from_client: " + inMessage);
							
							if(inMessage.intern() == REQUEST_GET_FILE) {
						        Blob pictureBlob = resultSet.getBlob("picture_blob");  
						        InputStream inputStream = pictureBlob.getBinaryStream();
						        BufferedInputStream bis = new BufferedInputStream(inputStream);
						        //DataOutputStream dataOutputStream2 = new DataOutputStream(socket.getOutputStream());
						        
						        //send file
						        byte [] mybytearray  = new byte [(int)pictureBlob.length()+1];
						        
						        //send size
						        dataOutputStream.writeInt((int) (pictureBlob.length()+1));
								System.out.println("to_client: " + String.valueOf(pictureBlob.length()+1));
						        
						        bis.read(mybytearray,0,mybytearray.length);
						        
						        System.out.println("Sending...");
						        dataOutputStream.write(mybytearray,0,mybytearray.length);
						        dataOutputStream.flush();
						        //outputStream.flush();
						        
						        
						    // REQUEST_NO_FILE
							} else {
								//do nothing
							}
						}
						
						/*
						 * text_content1
						 */
						if(stepType.intern() == "3") {
							outMessage = resultSet.getString("text_content1");
							dataOutputStream.writeUTF(outMessage);
							System.out.println("to_client: " + outMessage);
						}
						/*
						 * text_content1
						 * qr_code
						 */
						if(stepType.intern() == "7") {
							outMessage = resultSet.getString("text_content1");
							dataOutputStream.writeUTF(outMessage);
							System.out.println("to_client: " + outMessage);
							
							outMessage = resultSet.getString("qr_code");
							dataOutputStream.writeUTF(outMessage);
							System.out.println("to_client: " + outMessage);
						}
					}
					
					//#5
					outMessage = "END_SCRIPT_SEND";
					dataOutputStream.writeUTF(outMessage);
					System.out.println("to_client: " + outMessage);
				}
				
				/*
				 * #1 client: GET_USER
				 * #2 client: <login>
				 * #3 client: <password>
				 * #4 server: USER_OK / USER_FAIL
				 */
				if(inMessage.intern() == REQUEST_GET_USER) {
					String login = null;
					String password = null;
					
					//#2
					login = dataInputStream.readUTF();
					System.out.println("from_client: " + login);
					
					//#3
					password = dataInputStream.readUTF();
					System.out.println("from_client: " + password);
					
					//#4
					getUserStatement.setString(1, login);
					getUserStatement.setString(2, password);
					ResultSet resultSet = null;
					outMessage = RESPONSE_USER_FAIL;
					try {
						resultSet = getUserStatement.executeQuery();
						if(resultSet.next()) {
							if(resultSet.getInt(1) == 0) {
								outMessage = RESPONSE_USER_FAIL;
							} else {
								outMessage = RESPONSE_USER_OK;
							}
						} else {
							outMessage = RESPONSE_USER_FAIL;
						}
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					dataOutputStream.writeUTF(outMessage);
					System.out.println("to_client: " + outMessage);
				}
				
				System.out.println("Session ended.");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		   
			finally {
				/* close DB connection */
				/*
				if(dbConnection != null) {
					try {
						dbConnection.close();
		            } catch (SQLException e) {
		                // TODO Auto-generated catch block
		                e.printStackTrace();
		            }
				}
				*/
				  
				/* close socket connection */
				if( socket!= null) {
					try {
				  		socket.close();
				  	} catch (IOException e) {
				  		// TODO Auto-generated catch block
				  		e.printStackTrace();
				  	}
				}
		    
				if( dataInputStream!= null) {
				  	try {
				  		dataInputStream.close();
				  	} catch (IOException e) {
				  		// TODO Auto-generated catch block
				  		e.printStackTrace();
				  	}
				}
			    
				if ( dataOutputStream!= null) {
				  	try {
				  		dataOutputStream.close();
				  	} catch (IOException e) {
				  		// TODO Auto-generated catch block
				  		e.printStackTrace();
				  	}
				}
			}
		}
	}
}
