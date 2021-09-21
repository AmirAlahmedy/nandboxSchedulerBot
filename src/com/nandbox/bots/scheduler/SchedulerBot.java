package com.nandbox.bots.scheduler;
import java.util.regex.Pattern;

import java.time.Instant;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.TimeZone;

import com.nandbox.bots.api.Nandbox;
import com.nandbox.bots.api.Nandbox.Api;
import com.nandbox.bots.api.NandboxClient;
import com.nandbox.bots.api.data.Chat;
import com.nandbox.bots.api.data.User;
import com.nandbox.bots.api.inmessages.BlackList;
import com.nandbox.bots.api.inmessages.ChatAdministrators;
import com.nandbox.bots.api.inmessages.ChatMember;
import com.nandbox.bots.api.inmessages.ChatMenuCallback;
import com.nandbox.bots.api.inmessages.IncomingMessage;
import com.nandbox.bots.api.inmessages.InlineMessageCallback;
import com.nandbox.bots.api.inmessages.InlineSearch;
import com.nandbox.bots.api.inmessages.MessageAck;
import com.nandbox.bots.api.inmessages.PermanentUrl;
import com.nandbox.bots.api.inmessages.WhiteList;
import com.nandbox.bots.api.outmessages.TextOutMessage;
import com.nandbox.bots.api.outmessages.OutMessage;
import com.nandbox.bots.api.outmessages.PhotoOutMessage;
import com.nandbox.bots.api.outmessages.AudioOutMessage;
import com.nandbox.bots.api.outmessages.CancelScheduledOutMessage;
import com.nandbox.bots.api.outmessages.ContactOutMessage;
import com.nandbox.bots.api.outmessages.DocumentOutMessage;
import com.nandbox.bots.api.outmessages.VideoOutMessage;
import com.nandbox.bots.api.outmessages.VoiceOutMessage;
import com.nandbox.bots.api.outmessages.LocationOutMessage;
import com.nandbox.bots.api.outmessages.GifOutMessage;

import com.nandbox.bots.api.util.Utils;

import net.minidev.json.JSONObject;

class TimeZoneOffset{
	private int hour;
	private int minute;
	private char operator;
	public int getHour() {
		return hour;
	}
	public void setHour(int hour) {
		this.hour = hour;
	}
	public int getMinute() {
		return minute;
	}
	public void setMinute(int minute) {
		this.minute = minute;
	}
	public char getOperator() {
		return operator;
	}
	public void setOperator(char operator) {
		this.operator = operator;
	}
}


class Helper{
	
	final String helpMsg = "Commands:\r\n"
			+ "1. /setup_timezone Offset\r\n"
			+ "Sets up your time zone by setting the offset from the UTC/GMT. For example: '/setup_timezone -05:00' if your time zone is UTC/GMT -5\r\n"
			+ "or '/setup_timezone +02:00' if your time zone is UTC/GMT +2. In case your time zone is UTC/GMT just type: '/setup_timezone +0:00'\r\n"
			+ "\r\n"
			+ "2. /schedule time\r\n"
			+ "Sends any kind of schedule.\r\n"
			+ "time field format is any number of digits followed by 'm','h','d','w' which stand for minutes, hours, weeks, and months respectively, for example: '/schedule 1h Dental appointment',\r\n"
			+ " or it can take the format 'yyyy-MM-dd HH:mm:ss', for example: '/ischedule 2021-01-24 09:05:15'\r\n"
			+ " After you send this command, you will be asked to send the schedule message, which could be a text message, a video, a gif...etc\r\n"
			+ " \r\n"
			+ "3. /listSchedules\r\n"
			+ "Type it to list all the upcoming schedules.";
	
	public long GetWakeUpTime(int time,char format,long currentTime) {
		//format = 1 => minute
		//format = 2 => hours
		//format = 3 => days
		//format = 4 => weeks
		//otherwise => error
		
		
		long time_in_minutes = 0;
		if(format == 'm') {
			time_in_minutes = time;
		}
		else if (format == 'h') {
			time_in_minutes = time*60;
		}
		else if (format == 'd') {
			time_in_minutes = time*24*60;
		}
		else if (format == 'w') {
			time_in_minutes = time*7*24*60;
		}
		long currentTimeMillis = currentTime;
		long currentTimeSeconds = currentTimeMillis / 1000L;
		long currentTimeMinutes = (currentTimeSeconds/60L);
		long wakeUpEpoch = (currentTimeMinutes + time_in_minutes)*60*1000;
		return wakeUpEpoch;
	}
	
	public long timeStringToScheduledTime_A(String timeString,long sendingTime) {
		char format = timeString.charAt(timeString.length() - 1);
		int time = Integer.parseInt(timeString.substring(0, timeString.length()-1));
		long scheduledTime = GetWakeUpTime(time,format,sendingTime);
		return scheduledTime;
	}
	
	public long timeStringToScheduledTime_B(String timeString, long msToAdd) throws ParseException {
		Date wakeUpDate=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(timeString);
		long scheduledTime = wakeUpDate.getTime();
		System.out.println("msToAdd: " + msToAdd);
		return scheduledTime + msToAdd;
	}
	
	public OutMessage setMessageBasics(OutMessage message,String chatId,Long scheduledTime,Integer chatSettings,String toUserId) {
		
		message.setChatId(chatId);
		long reference = Utils.getUniqueId();
		message.setReference(reference);
		if(scheduledTime != null) {
			message.setScheduleDate(scheduledTime);
		}
		if(chatSettings != null &&chatSettings == 1) 
		{
			message.setChatSettings(1);
			message.setToUserId(toUserId);
		}
		return message;
	}
	
	public void sendConfirmationMessage(String chatId,Api api,int chatSettings,String toUserId) {
		TextOutMessage confirmationMessage = new TextOutMessage();
		confirmationMessage.setText("Schedule has been set");
		confirmationMessage = (TextOutMessage) setMessageBasics(confirmationMessage, chatId,null,chatSettings,toUserId);
		api.send(confirmationMessage);
		
		
	}
	
	public void sendClearMessage(String reference,String chatId,IncomingMessage incomingMsg,Api api)
	{
		TextOutMessage clearMessage = new TextOutMessage();
		clearMessage.setText("To clear this schedule, type '/clear "+reference+"'");
		clearMessage = (TextOutMessage) setMessageBasics(clearMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
		api.send(clearMessage);
	}
	
	public void sendClearMessageNew(String messageId,String chatId,int chatSettings,String userId,Api api)
	{
		//incomingScheduleMsg.getMessageId(), incomingScheduleMsg.getChat().getId(),1,incomingScheduleMsg.getFrom().getId(), api
		TextOutMessage clearMessage = new TextOutMessage();
		clearMessage.setText("To clear this schedule, type '/clear "+messageId+"'");
		clearMessage = (TextOutMessage) setMessageBasics(clearMessage, chatId, null,chatSettings,userId);
		api.send(clearMessage);
	}
	
	
	//Format checking using regex
	public boolean isHelpCommand(String messageText) {
		if(Pattern.compile("\\/help\\s*").matcher(messageText).matches()) {
			return true;
		}
		return false;
	}
	
	public boolean isSetupTimeZoneCommand(String messageText) {
		return Pattern.compile("/setup_timezone\\s+((\\+[0-1][0-4]?|\\-[0-1][0-4]?):([0-5][0-9]))").matcher(messageText).matches();
	}
	
	public boolean isClearCommand(String messageText) {
		if(Pattern.compile("\\/clear\\s+[a-zA-Z0-9_]+\\s*").matcher(messageText).matches()) {
			return true;
		}
		return false;
	}
	
	
	public boolean isListCommand(String messageText) {
		if(Pattern.compile("\\/listSchedules\\s*").matcher(messageText).matches()) {
			return true;
		}
		return false;
	}
	
	public boolean isiAlertCommand_A(String messageText) {
		if(Pattern.compile("\\/schedule\\s[0-9]+[m,h,d,w]\\s*").matcher(messageText).matches()) {
			return true;
		}
		return false;
	}
	
	public boolean isiAlertCommand_B(String messageText) {
		if(Pattern.compile("\\/schedule\\s[0-9]{4}-[0-9]{2}-[0-9]{2}\\s(([0-1][0-9])|(2[0-3])):[0-5][0-9]:[0-5][0-9]").matcher(messageText).matches()) {
			return true;
		}
		return false;
	}
	
	
	//Check whether the provided time is in the format "20m","5h","2d","2w"
	public boolean isTimeFormatA(String timeString) {
		if(Pattern.compile("[0-9]+[m,h,d,w]\\s*").matcher(timeString).matches()) {
			return true;
		}
		return false;
	}
	
	//Check whether the provided time is in the format "yyyy-MM-dd HH:mm:ss"
	public boolean isTimeFormatB(String timeString) {
		if(Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}\\s(([0-1][0-9])|(2[0-3])):[0-5][0-9]:[0-5][0-9]").matcher(timeString).matches()) {
			return true;
		}
		return false;
	}
}

public class SchedulerBot {
	static HashMap<String,String> refToChat = new HashMap<String,String>();
	static HashMap<String,String> chat_refToAdminID = new HashMap<String,String>();
	
	public static String getTokenFromPropFile() throws IOException {
		Properties prop = new Properties();

		InputStream input = new FileInputStream("token.properties");
		prop.load(input);
		return prop.getProperty("Token");
	}
	
	public static void main(String[] args) throws Exception {
		//The Helper class contains some helper functions which are called when needed
		final Helper help = new Helper();
		final Database db = new Database("alerts");
		
		
		
		NandboxClient client = NandboxClient.get();
		
		String BotToken = getTokenFromPropFile();
		
		client.connect(BotToken, new Nandbox.Callback() {
			Nandbox.Api api = null;
			
			@Override
			public void onConnect(Api api) {
				// it will go here if the bot connected to server successfully 
				System.out.println("Authenticated");
				try {
					db.createTable();
					db.createTimeZoneTable();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("Database table couldn't be created, please make sure your db is setup correctly");
					return;
				}
				this.api = api;
				
			}
			
			
			
			@Override
			public void onReceive(IncomingMessage incomingMsg) {
				
				
				//get the chat type, the chat ID, the user ID, and the time the message was sent which will be used later
				String chatType = incomingMsg.getChat().getType();
				String chatId = incomingMsg.getChat().getId();
				String userId = incomingMsg.getFrom().getId();
				long sendingTime = incomingMsg.getDate();
				
				
				//If this value timeString exists (not null) then the user has used the iAlert command and is now sending the alert message
				String timeString = refToChat.get(userId+chatId);
				if(timeString != null && ((((incomingMsg.isFromAdmin()==1) && incomingMsg.getChatSettings() == 1) || (!chatType.equals("Channel"))))) {
					refToChat.remove(userId+chatId);
					long scheduledTime = 0;
					if(help.isTimeFormatA(timeString)) 
					{
						scheduledTime = help.timeStringToScheduledTime_A(timeString, sendingTime);
					}
					
					else if(help.isTimeFormatB(timeString)) 
					{
						try 
						{
							
							TimeZoneOffset offset = db.getTimeZoneFromDB(chatId);
							
							int timeZoneHr = offset.getHour();
							int timeZoneMn = offset.getMinute();
							char operator = offset.getOperator();
							
							System.out.println("tzHr: " + timeZoneHr + " tzMn: " + timeZoneMn);
							
							long currentTimeZoneOffsetInMs =  TimeZone.getDefault().getOffset(0);
							
							System.out.println("currentTimeZoneOffsetInMs: " + currentTimeZoneOffsetInMs);
							
							long msToAdd = 0;
							if(operator == '+')
								msToAdd = timeZoneHr*60*60*1000 + timeZoneMn*60*1000 - currentTimeZoneOffsetInMs;
							else if(operator == '-')
								msToAdd = -(timeZoneHr*60*60*1000 + timeZoneMn*60*1000) - currentTimeZoneOffsetInMs;
							
							scheduledTime = help.timeStringToScheduledTime_B(timeString, msToAdd);
						} catch (ParseException e) 
						{
							TextOutMessage errorMessage = new TextOutMessage();
							errorMessage.setText("Please make sure you entered the date in the format yyyy-MM-dd HH:mm:ss");
							
							errorMessage = (TextOutMessage) help.setMessageBasics(errorMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
							api.send(errorMessage);
							return;
						} catch (SQLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						
						//Handling the case where the user sets the date format to be in a past time, or sends the alert message after the scheduled date had already passed
						long currentEpoch = Instant.now().toEpochMilli();
						if(currentEpoch > scheduledTime)
						{
							TextOutMessage errorMessage = new TextOutMessage();
							errorMessage.setText("The specified schedule time has already passed. Please make sure you send your schedule message before the specified schedule time");
							
							errorMessage = (TextOutMessage) help.setMessageBasics(errorMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
							api.send(errorMessage);
							return;
							
						}
					}
					
					else 
					{
						TextOutMessage errorMessage = new TextOutMessage();
						errorMessage.setText("Please make sure you entered the schedule command in the correct format. Type /help for more info");
						
						errorMessage = (TextOutMessage) help.setMessageBasics(errorMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						api.send(errorMessage);
						
						return;
					}
						
					
					
					if(incomingMsg.isAudioMsg()) 
					{
						AudioOutMessage message = new AudioOutMessage();
						message.setAudio(incomingMsg.getAudio().getId());
						message = (AudioOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);
					
					}
					
					else if(incomingMsg.isContactMsg()) 
					{
						ContactOutMessage message = new ContactOutMessage();
						message.setPhoneNumber(incomingMsg.getContact().getPhoneNumber());
						message.setName(incomingMsg.getContact().getName());
						message = (ContactOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);

					}
					
					else if(incomingMsg.isDocumentMsg()) 
					{
						DocumentOutMessage message = new DocumentOutMessage();
						message.setDocument(incomingMsg.getDocument().getId());
						message = (DocumentOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);

					}
					
					else if(incomingMsg.isGifMsg()) 
					{
						GifOutMessage message;
						//message.setGif(incomingMsg.getGif().getId());	
						String gifID = incomingMsg.getGif().getId();
						if(gifID.endsWith(".gif")) 
						{
							message = new GifOutMessage(GifOutMessage.GifType.PHOTO);
						}
						else
						{
							message = new GifOutMessage(GifOutMessage.GifType.VIDEO);
						}
						message.setGif(incomingMsg.getGif().getId());
						message = (GifOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);
					}
					
					else if(incomingMsg.isLocationMsg()) 
					{
						LocationOutMessage message = new LocationOutMessage();
						message.setLatitude(incomingMsg.getLocation().getLatitude());
						message.setLongitude(incomingMsg.getLocation().getLongitude());
						message = (LocationOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);
	
					}
					
					else if(incomingMsg.isPhotoMsg()) 
					{
						PhotoOutMessage message = new PhotoOutMessage();
						message.setPhoto(incomingMsg.getPhoto().getId());
						message.setCaption(incomingMsg.getCaption());
						message = (PhotoOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);
					}
					
					//Placeholder left to handle sticker alerts later
					//else if(incomingMsg.isStickerMsg()) 
					//{
					//	StickerOutMessage message = new StickerOutMessage();					
					//}
					
					else if(incomingMsg.isTextFileMsg() || incomingMsg.isTextMsg()) 
					{
						TextOutMessage message = new TextOutMessage();
						message.setText(incomingMsg.getText());
						message.setBgColor(incomingMsg.getBgColor());
						message = (TextOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						
						chat_refToAdminID.put(chatId+message.getReference(), userId);
						
						api.send(message);
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());						
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);
						
						
						
						

					}
					
					else if(incomingMsg.isVideoMsg()) 
					{
						VideoOutMessage message = new VideoOutMessage();
						message.setVideo(incomingMsg.getVideo().getId());
						message.setCaption(incomingMsg.getCaption());
						message = (VideoOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						
						api.send(message);
						
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);

					}
					
					else if(incomingMsg.isVoiceMsg()) 
					{
						VoiceOutMessage message = new VoiceOutMessage();
						message.setVoice(incomingMsg.getVoice().getId());
						message = (VoiceOutMessage) help.setMessageBasics(message, chatId, scheduledTime,null,incomingMsg.getFrom().getId());
						api.send(message);
						
						help.sendConfirmationMessage(chatId, api,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						//help.sendClearMessage(message.getReference().toString(), chatId, incomingMsg, api);
						
						
						
						//Placeholder left to implement Alert Editing feature
						/*String toUserId = message.getToUserId();
						long reference = message.getReference();
						JSONObject jsonMessage = message.toJsonObject();
						String messageId = (String) jsonMessage.get("message_id");
						MessageRecaller msgRecall = new MessageRecaller(chatId,messageId,toUserId,reference);
						scheduledMessages.add(msgRecall);*/
					}
					
					
					
					
					
					
				}
				

				//Check if the received message is a text message
				else if (incomingMsg.isTextMsg()) 
				{
					
					String messageText = incomingMsg.getText();
					
					
					
					//Help command
					if(help.isHelpCommand(messageText)) 
					{
						TextOutMessage message = new TextOutMessage();
						message.setText(help.helpMsg);
						
						message = (TextOutMessage) help.setMessageBasics(message, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
						api.send(message);
					}
					
					//Before we check if it follows the alert/ialert commands, make sure it's sent from an admin if it was sent in a chat of type Channel
					else if((((incomingMsg.isFromAdmin()==1) && incomingMsg.getChatSettings() == 1) || (!chatType.equals("Channel")))) 
					{
						
						//Clear command
						if(help.isClearCommand(messageText))
						{
							String[] messageSplit = messageText.split(" ",2);
							//String chatRef = 
							String messageId = messageSplit[1];
							System.out.println("Message id is: "+messageId);
							try {
								if(db.alertExists(messageId))
								{
									System.out.println("message can be cleared");
									CancelScheduledOutMessage cancel = new CancelScheduledOutMessage();
									cancel.setMessageId(messageId);
									cancel.setChatId(chatId);
									api.send(cancel);
									
									try {
										db.deleteAlert(messageId);
									} catch (SQLException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									
									TextOutMessage message = new TextOutMessage();
									message.setText("Schedule has been cleared");
									
									message = (TextOutMessage) help.setMessageBasics(message, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
									api.send(message);
								}
								else
								{
									TextOutMessage message = new TextOutMessage();
									message.setText("Schedule hasn't been cleared. It doesn't exist");
									
									message = (TextOutMessage) help.setMessageBasics(message, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
									api.send(message);
								}
							} catch (SQLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							return;
							
						}
						
						if(help.isListCommand(messageText))
						{
							ArrayList<ArrayList<String>> alerts = new ArrayList<ArrayList<String>>();
							try {
								System.out.println(chatId);
								System.out.println(userId);
								alerts = db.getAlertsByAdmin(chatId, userId);
								if(alerts.size() == 0)
								{
									TextOutMessage message = new TextOutMessage();
									message.setText("No schedules are set for you at the moment.");
									message = (TextOutMessage) help.setMessageBasics(message, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
									api.send(message);
								}
								else 
								{
									TextOutMessage message = new TextOutMessage();
									String scheduledAlerts = "";
									for(int i=0;i<alerts.size();i++)
									{
										String alertText = alerts.get(i).get(0);
										String alertMessageId = alerts.get(i).get(1);
										String alertDate = alerts.get(i).get(2);
										int j = i+1;
										scheduledAlerts += "Schedule "+j+": "+alertText+"\nSchedule Date: "+alertDate+"\nTo cancel, type '/clear "+alertMessageId+"'\n\n";
									}
									scheduledAlerts = scheduledAlerts.substring(0,scheduledAlerts.length()-3);
									message.setText(scheduledAlerts);
									message = (TextOutMessage) help.setMessageBasics(message, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
									api.send(message);
								}
								
							} catch (SQLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
							return;
						}

						
						if (help.isiAlertCommand_A(messageText) || help.isiAlertCommand_B(messageText)) 
						{
							String[] messageSplit = messageText.split(" ",2);
							timeString = messageSplit[1];
							
							
							//Send a confirmation message to the user to let him/her know that the alert has been set
							TextOutMessage confirmationMessage = new TextOutMessage();
							confirmationMessage.setText("Please send me your message to be scheduled");
							
							confirmationMessage = (TextOutMessage) help.setMessageBasics(confirmationMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
							api.send(confirmationMessage);
							
							
							refToChat.put(userId+chatId,timeString);
							return;
						}
						
						if(help.isSetupTimeZoneCommand(messageText)) {
							String[] messageSplit = messageText.split(" ", 2);
							String timeZoneOffset = messageSplit[1];
							
							String[] timeZoneSplit = timeZoneOffset.split(":", 2);
							String hoursOffset = timeZoneSplit[0];
							String minutesOffset = timeZoneSplit[1];
							
							
							char timeZoneOperator = hoursOffset.charAt(0);
							hoursOffset = hoursOffset.substring(1);
							
							int timeZoneHoursOffset = Integer.parseInt(hoursOffset);
							int timeZoneminutesOffset = Integer.parseInt(minutesOffset);
							
							try {
								if(db.chatIdExistsInTimeZoneTable(chatId)) {
									try {										
										db.updateTimeZoneForSpecificChatId(chatId, timeZoneHoursOffset, timeZoneminutesOffset, timeZoneOperator);
									} catch(SQLException e) {
										e.printStackTrace();
									}finally {
										TextOutMessage confirmationMessage = new TextOutMessage();
										confirmationMessage.setText("Time zone updated successfully!");
										
										confirmationMessage = (TextOutMessage) help.setMessageBasics(confirmationMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
										api.send(confirmationMessage);
									}
									
								}else {
									try {
										db.insertTimeZone(chatId, timeZoneHoursOffset, timeZoneminutesOffset, timeZoneOperator);
									} catch (SQLException e) {
										e.printStackTrace();
									}finally {
										TextOutMessage confirmationMessage = new TextOutMessage();
										confirmationMessage.setText("Time zone saved successfully!");
										
										confirmationMessage = (TextOutMessage) help.setMessageBasics(confirmationMessage, chatId, null,incomingMsg.getChatSettings(),incomingMsg.getFrom().getId());
										api.send(confirmationMessage);
									}
								}
							} catch (SQLException e) {
								e.printStackTrace();
							}
							
						}
						
					}
					
				}
				

		}
			// implement other nandbox.Callback() as per your bot need .

			@Override
			public void onReceive(JSONObject obj) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onClose() {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onError() {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onChatMenuCallBack(ChatMenuCallback chatMenuCallback) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onInlineMessageCallback(InlineMessageCallback inlineMsgCallback) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onMessagAckCallback(MessageAck msgAck) {
				// TODO Auto-generated method stub
				System.out.println("msgack HERE");
				//check if message is in DB
				try {
					if(db.alertExists(msgAck.getMessageId()))
					{
						db.deleteAlert(msgAck.getMessageId());
						
					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			@Override
			public void onUserJoinedBot(User user) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onChatMember(ChatMember chatMember) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onChatAdministrators(ChatAdministrators chatAdministrators) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void userStartedBot(User user) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onMyProfile(User user) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onUserDetails(User user) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void userStoppedBot(User user) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void userLeftBot(User user) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void permanentUrl(PermanentUrl permenantUrl) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onChatDetails(Chat chat) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onInlineSearh(InlineSearch inlineSearch) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onBlackList(BlackList blackList) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onWhiteList(WhiteList whiteList) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onScheduleMessage(IncomingMessage incomingScheduleMsg) {
				
				//if channel chat
				
				if(incomingScheduleMsg.getSentTo() == null)
				{
					String adminId = chat_refToAdminID.get(incomingScheduleMsg.getChat().getId()+incomingScheduleMsg.getReference());
					help.sendClearMessageNew(incomingScheduleMsg.getMessageId(), incomingScheduleMsg.getChat().getId(),1,adminId, api);
					try {
						Date scheduleDate = new Date(incomingScheduleMsg.getScheduleDate());
						DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		                String strDate = dateFormat.format(scheduleDate); 
						db.insertAlert(incomingScheduleMsg.getChat().getId(), adminId, incomingScheduleMsg.getMessageId(),strDate, incomingScheduleMsg.getText());
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						chat_refToAdminID.remove(incomingScheduleMsg.getChat().getId()+incomingScheduleMsg.getReference());
						e.printStackTrace();
					}
					chat_refToAdminID.remove(incomingScheduleMsg.getChat().getId()+incomingScheduleMsg.getReference());

				}
				else
				{
					help.sendClearMessageNew(incomingScheduleMsg.getMessageId(), incomingScheduleMsg.getSentTo().getId(),incomingScheduleMsg.getChatSettings(),incomingScheduleMsg.getFrom().getId(), api);
					try {
						
						Date scheduleDate = new Date(incomingScheduleMsg.getScheduleDate());
						DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
		                String strDate = dateFormat.format(scheduleDate); 
		                
						db.insertAlert(incomingScheduleMsg.getSentTo().getId(), incomingScheduleMsg.getSentTo().getId(), incomingScheduleMsg.getMessageId(),strDate, incomingScheduleMsg.getText());
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}				
			}
		});
	}
}
