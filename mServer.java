/*******************************************************************************
 * @author		: Rohan Jyoti
 * @filename	: mServer.java
 * @purpose		: Server responsible for storing client files (files come in
 * 					encrypted)
 ******************************************************************************/


import java.io.*;
import java.net.*;
import java.util.*;

import javax.crypto.spec.IvParameterSpec;

public class mServer 
{
	//===============Private Variables
	private static int port = 20000;
	private static final int FILE_TRANSACTION_UP = 0;
	private static final int FILE_TRANSACTION_DOWN = 1;
	private static final int COMMAND_EXECUTION = 1;
	
	private static final int COMMAND_EXECUTION_SEARCH = 2;
	private static final int COMMAND_EXECUTION_ADD = 3;
	private static final int COMMAND_EXECUTION_DELETE = 4;
		
	private static List<String> mFilesList = new ArrayList<String>();
	private static Dictionary<String, IvParameterSpec> mIVDictionary = new Hashtable<String, IvParameterSpec>();
	private static String serverSuffix = "_cServerENCRYPTED";
			
			
	//===============Public functions
	public static void main(String[] args) throws IOException, InterruptedException
	{
		ServerSocket mServerSocket = new ServerSocket(port);
		
		while(true)
		{
			Socket mSocket = mServerSocket.accept();
			DataInputStream mData = new DataInputStream(mSocket.getInputStream());
			
			char transactionType = mData.readChar();
			if(transactionType == FILE_TRANSACTION_UP)
			{
				//Next we receive numFiles and iterate
				int numFiles = mData.readInt();
				for(int i=0; i<numFiles; i++)
					mRecvFile(mData);
			}
			else if(transactionType == FILE_TRANSACTION_DOWN)
			{
				DataOutputStream mDOS = new DataOutputStream(mSocket.getOutputStream());
				
				//next we receive numFiles and iterate
				int numFiles = mData.readInt();
				for(int i=0; i<numFiles; i++)
					mSendFile(mData, mDOS);
			}
			else if(transactionType == COMMAND_EXECUTION)
			{
				String mResponse = mExecGrep(mData);
				DataOutputStream mDOS = new DataOutputStream(mSocket.getOutputStream());
				mDOS.writeUTF(mResponse);
				mDOS.flush();
			}
			else if(transactionType == COMMAND_EXECUTION_DELETE)
			{
				//we simply read the incoming filename and delete it accordingly
				
				//we receive numFiles and iterate
				int numFiles = mData.readInt();
				
				for(int i=0; i<numFiles; i++)
				{
					String fileName = mData.readUTF();
					String nFName = fileName.concat(serverSuffix);
					File tFile = new File(nFName);
					tFile.delete();
					
					SOP("File: " + nFName + " was deleted off the server.");
				}
				
			}
			else
				SOP("Invalid transactionType: " + transactionType +". Connection Closed");
			
			mSocket.close();
		}
	}
		
		
	/*******************************************************************************
	 * @author	: Rohan Jyoti
	 * @name	: mRecvFile
	 * @class	: CiphertextServer
	 * @param	: DataInputStream mData (associated input stream with socket)
	 * @return	: void
	 * @purpose	: Receives files from client and writes to disk with specified
	 * 				serverSuffix.
	 ******************************************************************************/
	public static void mRecvFile(DataInputStream mData) throws IOException
	{
		//First we read the name...
		String fileName = mData.readUTF();
		//Then the fileSize...
		long fileSize = mData.readLong();
		//SOP("Filename[Filesize] --> " + fileName + "[" + fileSize +"]");
		SOP("[" + fileName + "] uploaded");
		
		//Then we write the data to file
		String fileNameOnServer = fileName.concat(serverSuffix);
		OutputStream mOS = new FileOutputStream(fileNameOnServer);
		byte[] mBuffer = new byte[(int)fileSize];
		int mBytesRead = 0;
		while(true)
		{
			int curr = mData.read(mBuffer, mBytesRead, (int) fileSize - mBytesRead);
			if(curr == -1 || curr == 0) break; //i.e. we have reached end of stream
			else mBytesRead += curr;
		}
		if(mBytesRead != fileSize)
		{
			SOP("File Size Mismatch");
			System.exit(1);
		}
		mOS.write(mBuffer, 0, mBytesRead);
			
			
			
		//here we should read the IV and save that to dictionary
		long tIV_size = mData.readLong();
		byte[] tIV = new byte[(int)tIV_size];
		mBytesRead = mData.read(tIV, 0, (int)tIV_size);
		if(mBytesRead != tIV_size)
		{
			SOP("IV Size Mismatch");
			System.exit(1);
		}
			
		IvParameterSpec tIVSpec = new IvParameterSpec(tIV);
		mIVDictionary.put(fileNameOnServer, tIVSpec);
		
		//Finally, we add this file to our global mFilesList
		mFilesList.add(fileNameOnServer);
		mOS.close();
		
		
		SOP("File and IV loaded onto server.");
	}
			
		
	/*******************************************************************************
	 * @author	: Rohan Jyoti
	 * @name	: mExecGrep
	 * @class	: CiphertextServer
	 * @param	: DataInputStream mData (associated input stream with socket)
	 * @return	: String (the result of the grep call)
	 * @purpose	: Implements system call for grep
	 *
	 * 				THIS FUNCTION IS NOT USED BY CIPHERTEXT CLIENT
	 * 				THIS FUNCTION SIMPLY SERVES AS A PLACEHOLDER
	 ******************************************************************************/
	public static String mExecGrep(DataInputStream mData) throws IOException, InterruptedException
	{
		SOP("Received Command To Execute Grep");
		String keyword = mData.readUTF();
		SOP("With keyword: " + keyword);
				
		List<String> mCommand = new ArrayList<String>();
		mCommand.add("grep");
		mCommand.add(keyword);
		if(mFilesList.size() == 0)
		{
			SOP("No files on Server. Please upload files to server.");
			return "";
		}
		mCommand.addAll(mFilesList);
		
		ProcessBuilder mPB = new ProcessBuilder(mCommand);
		Process mProcess = mPB.start();
		
		//Next we wait for response
		InputStream mProcessOutput = mProcess.getInputStream();
		//int mExitStatus = mProcess.waitFor();
		//SOP("Exit Status: " + mExitStatus);
		String mResponse = mInputStreamToString(mProcessOutput);
		mProcessOutput.close();
		return mResponse;
	}
		
		
	/*******************************************************************************
	 * @author	: Rohan Jyoti
	 * @name	: mInputStreamToString
	 * @class	: CiphertextServer
	 * @param	: InputStream mIS (associated input stream with some data)
	 * @return	: String (the result of the conversion)
	 * @purpose	: Converts specified input stream to UTF-8 String
	 ******************************************************************************/
	public static String mInputStreamToString(InputStream mIS) throws IOException
	{
		if(mIS == null)
			return "";
		else
		{
			Writer mWriter = new StringWriter();
			char[] mBuffer = new char[1024];
			
			Reader mReader = new BufferedReader(new InputStreamReader(mIS, "UTF-8"));
			
			int mBytesRead;
			while((mBytesRead = mReader.read(mBuffer)) != -1)
				mWriter.write(mBuffer, 0, mBytesRead);
			
			return mWriter.toString();
		}
	}
		
	
	/*******************************************************************************
	 * @author	: Rohan Jyoti
	 * @name	: mSendFile
	 * @class	: CiphertextServer
	 * @param	: DataInputSteeam mData (input stream associated with accepting socket),
	 * 				DataOuputStream mDOS (output stream to communicate and send file
	 * 				client)
	 * @return	: void
	 * @purpose	: Sends files to client upon download request from client
	 ******************************************************************************/
	public static void mSendFile(DataInputStream mData, DataOutputStream mDOS) throws IOException
	{
		//First we read the name...
		String fileName = mData.readUTF();
		//Recall that on the server, the files are stored as fileName_cServer
		String fileNameOnServer = fileName.concat(serverSuffix);
		byte[] tFile = mReadFile(fileNameOnServer);
		
		//So now we can send the size and the data
		mDOS.writeLong(tFile.length);
		mDOS.write(tFile, 0, tFile.length);
		
		//Next we send the IV size and data
		IvParameterSpec tIV = mIVDictionary.get(fileNameOnServer);
		mDOS.writeLong(tIV.getIV().length);
		mDOS.write(tIV.getIV(), 0, tIV.getIV().length);
		
		mDOS.flush();
	}
		
		
	/*******************************************************************************
	 * @author	: Rohan Jyoti
	 * @name	: mReadFile
	 * @class	: CiphertextServer
	 * @param	: String fileName (the file you want to read)
	 * @return	: byte[] (byte array of file )
	 * @purpose	: Reads specified file and returns file in byte-order form
	 ******************************************************************************/
	public static byte[] mReadFile(String fileName) throws IOException
	{
		//===========We will read the file into a data buffer
		File mFile = new File(fileName);
		byte[] mByteArray = new byte[(int) mFile.length()]; //This is the buffer we will read into
		
		//In order to read a file, we will create a DataInputStream off a BufferedInputStream, which in turn
		//depends on the FileInputStream
		FileInputStream mFIS = new FileInputStream(mFile);
		BufferedInputStream mBIS = new BufferedInputStream(mFIS);
		DataInputStream mDIS = new DataInputStream(mBIS);
		mDIS.readFully(mByteArray, 0, mByteArray.length);
		
		return mByteArray;
	}
		
	/*******************************************************************************
	 * @author	: Rohan Jyoti
	 * @name	: SOP
	 * @class	: CiphertextServer
	 * @param	: String arg (any arbitrary argument as string)
	 * @return	: void
	 * @purpose	: Easier to type than System.out.println
	 ******************************************************************************/
	public static void SOP(String arg)
	{
		System.out.println(arg);
	}
}
