/*******************************************************************************
 * @author		: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
 * @filename	: mClient.java
 * @purpose		: Client responsible for building index, uploading encrypted files,
 * 					adding files, deleting files, searching files
 ******************************************************************************/


import java.io.*;
import java.net.*;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class mClient 
{
	//===============Private Variables
	private static String IP_Addr;
	private static int port;
			
	private static final int FILE_TRANSACTION_UP = 0;
	private static final int FILE_TRANSACTION_DOWN = 1;
	private static final int COMMAND_EXECUTION_SEARCH = 2;
	private static final int COMMAND_EXECUTION_ADD = 3;
	private static final int COMMAND_EXECUTION_DELETE = 4;
		
	private static String clientTempSuffix = ""; //"_ccDECRYPTED";
	
	private static mClient mClient = new mClient();
	
	private static Socket mClientSocket;
	
	//===============Public functions
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException 
	{
		
		if(args.length < 3)
		{
			SOP("Invalid Syntax. Usage is as follow: ");
			SOP("java mClient serverIP serverPort upload/add/delete file1 [...]");
			SOP("-----OR-----");
			SOP("java mClient serverIP serverPort search keyword [downloadDirectory]");
			System.exit(1);
		}
		
		IP_Addr = args[0];
		port = Integer.parseInt(args[1]);
		//===========First we try to create client socket (will use localhost for right now)
		mClientSocket = new Socket(IP_Addr, port);
		
		
		String actionType = args[2];
                
		BuildIndex mIndex = mClient.new BuildIndex();
		String filesFromArgs[] = new String[args.length - 3];
		for(int i=0; i<filesFromArgs.length; i++)
		{
			filesFromArgs[i] = args[i+3];
		}

		if(actionType.equalsIgnoreCase("upload") || actionType.equalsIgnoreCase("add"))
		{
			//Updating index
			long startTime = System.currentTimeMillis();
                    
			String[][] toUpload = mIndex.addFiles(filesFromArgs);
                        
                        long midTime = System.currentTimeMillis();
                        long timeTaken = midTime - startTime;
                        
                        SOP("Time to index : " + timeTaken + " milliseconds");
			
			//toUpload[][0] - Index number
			//toUpload[][1] - File's original path
			//StringBuilder filesList = new StringBuilder();
			
			SOP("To upload: ");
			int numFiles = toUpload.length;
			if(toUpload!=null)
			{
				for (int i=0; i<toUpload.length; i++)
				{
					//SOP(toUpload[i][1]);
				}
			}
				
			//Need to edit the code to upload all the files of a directory
			SecretKeySpec mSecretKeySpec = mGenerateSessionKey();
		
			
			//First we create the DataOutputStream here and send over the transaction type and num_files
			DataOutputStream mDOS = new DataOutputStream(mClientSocket.getOutputStream());
			//First we let the server know that this is a file upload transaction
			mDOS.writeChar(FILE_TRANSACTION_UP);
			mDOS.writeInt(numFiles);

			for(int i=0; i<toUpload.length; i++)
			{
				//filesList.append(args[i]).append("\n");

				byte[] mUnencryptedData = mReadFile(toUpload[i][1]);
				IvParameterSpec mIV = mGenerateIV();
				byte[] mEncryptedData = mEncryptFile(mUnencryptedData, mSecretKeySpec, mIV);
				mUploadFile(toUpload[i][0], mEncryptedData, mIV, mDOS);
			}
				
			//writeFilesList(filesList.toString());
                        
			long endTime = System.currentTimeMillis();
                        
			long diffTime = endTime - startTime;
			System.out.println("Time to add files: " + diffTime+ " millisecond(s)");
		}
		else if(actionType.equalsIgnoreCase("delete"))
		{
			long startTime = System.currentTimeMillis();
                        
			int[] fileIndexes = mIndex.deleteFiles(filesFromArgs);
			
			//Next we create the DataOutputStream here and send over the transaction type and num_files
			DataOutputStream mDOS = new DataOutputStream(mClientSocket.getOutputStream());
			//Then we let the server know that this is a file upload transaction
			mDOS.writeChar(COMMAND_EXECUTION_DELETE);
			int numFiles = fileIndexes.length;
			mDOS.writeInt(numFiles);
			
			for(int i =0; i<fileIndexes.length; i++)
			{
				int fileToDelete = fileIndexes[i];
				if(fileToDelete != 0)
				{
					SOP("About to delete file: " + fileToDelete);
					mDeleteFile(Integer.toString(fileToDelete), mDOS);
				}
			}
                        
			long endTime = System.currentTimeMillis();
                        
			long diffTime = endTime - startTime;
			System.out.println("Time to delete files: " + diffTime+ " millisecond(s)");
			
		}
		else if(actionType.equalsIgnoreCase("search"))
		{
			long startTime = System.currentTimeMillis();
                        
			if(args.length > 4) //meaning user has specified a download directory
				mExecSearch(args[3], args[4]); //args[3] is the search keyword and args[4] is download dir
			else
				mExecSearch(args[3], "ClientDownloadedFiles");
                        
			long endTime = System.currentTimeMillis();
                        
			long diffTime = endTime - startTime;
			System.out.println("Time to search keyword " + args[3] + " : " + diffTime+ " millisecond(s)");
		}
		else
			SOP("Invalid Action Type. Options include 'upload', 'add', 'delete' or 'search'");
	}
        
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: writeFilesList
	 * @class	: mClient
	 * @param	: String tFilesList (string representation of all files on server
	 * 				associated with client)
	 * @return	: void
	 * @purpose	: Writes to disk a list of all files associated with client on server
	 ******************************************************************************/
	public static void writeFilesList(String tFilesList) throws IOException
	{
		FileWriter mCFLFW = new FileWriter("client_files_list");
		BufferedWriter mFWOUT = new BufferedWriter(mCFLFW);
		mFWOUT.write(tFilesList);
		mFWOUT.close();	
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: readFilesList
	 * @class	: mClient
	 * @param	: none
	 * @return	: List<String> (of files)
	 * @purpose	: Reads list of files associated with client on server)
	 ******************************************************************************/
	public static List<String> readFilesList() throws IOException
	{
		FileReader mCFLFR = new FileReader("client_files_list");
		BufferedReader mFRIN = new BufferedReader(mCFLFR);
		List<String> mFilesList = new ArrayList<String>();
		String tFileName;
		while((tFileName = mFRIN.readLine()) != null)
			mFilesList.add(tFileName);
		
		return mFilesList;	
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mGenerateSessionKey
	 * @class	: mClient
	 * @param	: none
	 * @return	: SecretKeySpec (spec container for session key)
	 * @purpose	: Generates AES-128 session key stored on disk
	 ******************************************************************************/
	public static SecretKeySpec mGenerateSessionKey() 
	throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, 
	InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{
		/*
		 * On beginning of an upload session, we will generate session key
		 * We will store this key to file
		 * 
		 * Then, when uploading the file to server, we will send the IV as well. 
		 * Server will store IV in ivDictionary associated with file
		 * 
		 * Then on grep, server will send back the IV and encrypted data
		 * Client will decrypt using stored key (from file) and retrieved IV, then perform
		 * grep on the files. (decrypted files will be saved as filename_dC, so perform Grep on these)
		 * After the grep is performed, rm -f the *_dC files.
		 */
		
		File keyfile = new File("sessionKey");
		
		if(keyfile.exists()){
            //If file exist, take the key from this file for decryption
			BufferedInputStream keyIn = new BufferedInputStream(new FileInputStream("sessionKey"));
			System.out.println("Using the key from the keyfile");
			byte[] mKey = new byte[16];
			keyIn.read(mKey, 0, 16);
			SecretKeySpec mSecretKeySpec = new SecretKeySpec(mKey, "AES");
			return mSecretKeySpec;
        }
		else
		{
		
			//First we will generate a 128 AES key
			KeyGenerator mKeyGen = KeyGenerator.getInstance("AES");
			mKeyGen.init(new SecureRandom());
			byte[] mKey = mKeyGen.generateKey().getEncoded();
			SecretKeySpec mSecretKeySpec = new SecretKeySpec(mKey, "AES");
		
		
			//Next we will save it to disk
			mWriteBytesToFile("sessionKey", mKey);
			return mSecretKeySpec;
		}
		
		
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mWriteBytesToFile
	 * @class	: mClient
	 * @param	: String fileName (the file you want to write to),
	 * 				byte[] mData (the data you want to write)
	 * @return	: void
	 * @purpose	: Writes specified byte array to specified file
	 ******************************************************************************/
	public static void mWriteBytesToFile(String fileName, byte[] mData) throws IOException
	{
		OutputStream mOS = new FileOutputStream(fileName);
		mOS.write(mData, 0 , mData.length);
		mOS.close();
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mReadFile
	 * @class	: mClient
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
		mFIS.close();
		return mByteArray;
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mEncryptFile
	 * @class	: mClient
	 * @param	: byte[] mUnencryptedData (the unencrypted data to be encrpyted),
	 * 				SecretKeySpec mSecretKeySpec (spec container for session key),
	 * 				IvParameterSpec mIV (spec container for associated IV)
	 * @return	: byte[] (the encrypted file in byte-order form)
	 * @purpose	: Encrypts data based on session key and associated IV
	 ******************************************************************************/
	public static byte[] mEncryptFile(byte[] mUnencryptedData, SecretKeySpec mSecretKeySpec, IvParameterSpec mIV) 
	throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, 
	InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{		
		//initialize cipher
		Cipher mCipher = Cipher.getInstance("AES/CTR/NoPadding");
		mCipher.init(Cipher.ENCRYPT_MODE, mSecretKeySpec, mIV);
		
		//Encrypt the message
		byte[] mEncryptedData = mCipher.doFinal(mUnencryptedData);
		return mEncryptedData;	
	}
		
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mDecryptFile
	 * @class	: mClient
	 * @param	: byte[] mEncryptedData (the encrypted data to be decrypted),
	 * 				SecretKeySpec mSecretKeySpec (spec container for session key),
	 * 				IvParameterSpec mIV (spec container for associated IV)
	 * @return	: byte[] (the decrypted file in byte-order form)
	 * @purpose	: Decrypts data based on session key and associated IV
	 ******************************************************************************/
	public static byte[] mDecryptFile(byte[] mEncryptedData, SecretKeySpec mSecretKeySpec, IvParameterSpec mIV)
	throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, 
	InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{
		//initialize cipher
		Cipher mCipher = Cipher.getInstance("AES/CTR/NoPadding");
		mCipher.init(Cipher.DECRYPT_MODE, mSecretKeySpec, mIV);
		
		//decrypt the message
		//byte[] mDecryptedData = new byte[(int) mEncryptedData.length()];
		byte[] mDecryptedData = mCipher.doFinal(mEncryptedData);
		
		return mDecryptedData;	
	}
		
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mGenerateIV
	 * @class	: mClient
	 * @param	: none
	 * @return	: IvParameterSpec (spec container for IV)
	 * @purpose	: Generates IV using SecureRandom
	 ******************************************************************************/
	public static IvParameterSpec mGenerateIV() throws NoSuchAlgorithmException, NoSuchPaddingException
	{
		//Generates Initialization Vector from Random numbers
		
		SecureRandom mSecureRandomGen = new SecureRandom();
		
		byte[] mIV = new byte[16];
		mSecureRandomGen.nextBytes(mIV);
		
		IvParameterSpec mIVSpec = new IvParameterSpec(mIV);
		return mIVSpec;
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mUploadFile
	 * @class	: mClient
	 * @param	: String fileName (the file you want to read),
	 * 				byte[] mData (the byte-order representation of the file)
	 * @return	: void
	 * @purpose	: Uploads file to user specified server
	 ******************************************************************************/
	public static void mUploadFile(String fileName, byte[] mData, IvParameterSpec mIV, DataOutputStream mDOS) throws IOException
	{
		
		//==========Next we send the file and IV to server over socket
		
		//Next we send over the filename...
		mDOS.writeUTF(fileName);
		//Then we send the file size...
		mDOS.writeLong(mData.length);
		//Then we send the data content...
		mDOS.write(mData, 0, mData.length);
		
		//Lastly, we send over the corresponding IV
		mDOS.writeLong(mIV.getIV().length);
		mDOS.write(mIV.getIV(), 0, mIV.getIV().length);
		
		
		mDOS.flush();
		//mClientSocket.close();
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mDeleteFile
	 * @class	: mClient
	 * @param	: String fileName (the file you want to delete from server
	 * @return	: void
	 * @purpose	: Sends command to server to delete files
	 ******************************************************************************/
	public static void mDeleteFile(String fileName, DataOutputStream mDOS) throws IOException
	{
		//Next we send over the filename...
		mDOS.writeUTF(fileName);
		
		mDOS.flush();
		//mClientSocket.close();
	}
	
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mDownloadFile
	 * @class	: CiphertextClient
	 * @param	: String fileName (the file you want to read),
	 * @return	: void
	 * @purpose	: Downloads file and associated IV, decrypts, and writes to disk
	 ******************************************************************************/
	public static void mDownloadFile(String fileName, String downloadFileAs, DataOutputStream mDOS)
	throws UnknownHostException, IOException, InvalidKeyException, NoSuchAlgorithmException, 
	NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{
		//===========First we try to create client socket (will use localhost for right now)
		//Socket mClientSocket = new Socket(IP_Addr, port);
		
		//Next we send over the filename...
		mDOS.writeUTF(fileName);
		
		
		//Then we receive the file...
		DataInputStream mDIS = new DataInputStream(mClientSocket.getInputStream());
		long fileSize = mDIS.readLong();
		byte[] mEncryptedData = new byte[(int)fileSize];
		int mBytesRead = 0;
		while(true)
		{
			 int curr = mDIS.read(mEncryptedData, mBytesRead, (int) fileSize - mBytesRead);
			 if(curr == -1 || curr == 0) break; //i.e. we have reached end of stream
			 else mBytesRead += curr;
		}
		if(mBytesRead != fileSize)
		{
			SOP("File Size Mismatch");
			System.exit(1);
		}
		
		
		//Then we receive the IV...
		long tIVSize = mDIS.readLong();
		byte[] tIV = new byte[(int)tIVSize];
		mBytesRead = mDIS.read(tIV, 0, (int)tIVSize);
		if(mBytesRead != tIVSize)
		{
			SOP("IV Size Mismatch");
			System.exit(1);
		}
		
		IvParameterSpec tIVSpec = new IvParameterSpec(tIV);
		
		//Decrypt then save
		byte[] tSessionKey = mReadFile("sessionKey");
		SecretKeySpec mSKS_file = new SecretKeySpec(tSessionKey, "AES");
		byte[] mDecryptedData = mDecryptFile(mEncryptedData, mSKS_file, tIVSpec);
		
		String tempFile = "";
		if(downloadFileAs == null)
			tempFile = fileName.concat(clientTempSuffix);
		else
			tempFile = downloadFileAs.concat(clientTempSuffix);

		//First we make sure the directory exists
		String ch = File.separator;
		if(ch.equals("\\\\"))
		{
			ch = "\\";
		}
		File tDir = new File(tempFile.substring(0, tempFile.lastIndexOf(ch)));
		//if(!tDir.exists())
                
                //File tDir = new File(tempFile);
		if(!tDir.exists())
			tDir.mkdirs();
		
		mWriteBytesToFile(tempFile, mDecryptedData);
		
		
		mDOS.flush();
		//mClientSocket.close();
	}

	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: mExecSearch
	 * @class	: CiphertextClient
	 * @param	: String tKeyword (search keyword)
	 * @return	: void
	 * @purpose	: Will search through local index and download files
	 ******************************************************************************/
	public static void mExecSearch(String tKeyword, String downloadDir)
	throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, 
	InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{
                
		BuildIndex mIndex = mClient.new BuildIndex();
		String[][] result = mIndex.searchKey(tKeyword);
		SOP("Result: ");
		if(result!=null)
		{
			//==========Next we receive the file and IV from server over socket
			DataOutputStream mDOS = new DataOutputStream(mClientSocket.getOutputStream());
			//First we let the server knew that this a file download transaction
			mDOS.writeChar(FILE_TRANSACTION_DOWN);
			int numFiles = result.length;
			mDOS.writeInt(numFiles);
			
			for (int i=0; i<result.length; i++)
			{
				SOP(result[i][1]);
				SOP("About to Download --> " + result[i][0]);
				
				//Before we download the file, we must modify the string and splice in the user specified
				//folder to put the downloaded files in.
				File currDir = new File("");
				File downDir = new File(downloadDir);
				String newDir;
				try
				{
					String origPath = result[i][1];
					String currDirrAbsPath = currDir.getAbsolutePath();
					//SOP(currDirrAbsPath);
					int cDAP_length = currDirrAbsPath.length();
                                        
                                        if(downDir.exists())
                                        {
                                                if(downDir.isDirectory())
                                                {
                                                        newDir = currDirrAbsPath.concat(File.separator + downloadDir).concat(origPath.substring(cDAP_length, origPath.length()));
                                                }
                                                else
                                                {
                                                    throw new IOException();
                                                }
                                        }
                                        else
                                        {
                                                downDir.mkdirs();
                                                newDir = currDirrAbsPath.concat(File.separator + downloadDir).concat(origPath.substring(cDAP_length, origPath.length()));
                                        }
                                        
					SOP("New Dir: " + newDir);
																			   
					
				}
				catch(Exception e)
				{
					SOP("Exception in currDir (mExecSearch): " + e.getMessage());
					SOP("File Not Downloaded...");
					return;
				}
				
				mDownloadFile(result[i][0], newDir, mDOS);
			}
		}
                
	}
	
	/*******************************************************************************
	 * @author	: Gaurav Lahoti, Rohan Jyoti, Seeun Oh
	 * @name	: SOP
	 * @class	: CiphertextClient
	 * @param	: String arg (any arbitrary argument as string)
	 * @return	: void
	 * @purpose	: Easier to type than System.out.println
	 ******************************************************************************/
	public static void SOP(String arg)
	{
		System.out.println(arg);
	}
	
		
	//Seeun Oh and Gaurav lahoti
	class BuildIndex 
	{
            
            private HashMap<String, String> kindex = new HashMap<String, String>();//store keyword and fileindex
            private HashMap<String, String> findex = new HashMap<String, String>();//store filename and fileindex
            
            private String fileMapFile = "fileMap.txt";
            private String wordIndexFile = "wordIndex.txt";
            
		/********************************************************************************************************************************
                ******************************************************************************************************************************** 
		 ****** 1. updateWordIndex : Create a index file   ***********************************************************************************
		 ****** - Param : String[] filenames          ***********************************************************************************
		 ****** - Return : true(Success), false(Fail) ***********************************************************************************
		 ******************************************************************************************************************************** 
                ********************************************************************************************************************************/
            public String[][] addFiles(String[] source) throws IOException
	    {                            
                    //Reads from the file and updates the global hashmap
                    findex = readHashMap(fileMapFile);
                    kindex = readHashMap(wordIndexFile);
                    
                    long indexStartTime = System.currentTimeMillis();

                    //Updates the Global Hashmap and returns a local hashmap containing only the new files
                    HashMap <String,String> newFileMap = null;
                    newFileMap = addFileMap(source);

                    String[][] toUpload = new String[newFileMap.size()][2];

                    if(!newFileMap.isEmpty())
                    {
                            Set fileKeySet = newFileMap.keySet();
                            Iterator Ir_fileKeySet = fileKeySet.iterator();

                            //create filename index Hashmap
                            //FileReader fin = null;
                            int i=0;
                            long startTime = System.currentTimeMillis();
                            long endTime;
                            while (Ir_fileKeySet.hasNext()) 
                            {
                                    String keyword = Ir_fileKeySet.next().toString();
                                    
                                    toUpload[i][0] = keyword;
                                    toUpload[i][1] = newFileMap.get(keyword);
                                    addKeywordMap(Integer.parseInt(keyword));
                                    endTime = System.currentTimeMillis();
                                    if((endTime - startTime) > 120000)
                                    {
                                        long indexTime = endTime - indexStartTime;
                                        System.out.println("Files Indexed : " + i + "\t     Total indexing time: " + indexTime + "milliseconds");
                                        startTime = System.currentTimeMillis();
                                    }
                                    ++i;
                            }

                            //if(fin!=null){fin.close();}
                            writeHashMap(findex,fileMapFile);
                            writeHashMap(kindex,wordIndexFile);
                    }
                    return toUpload;
            }
            
            /********************************************************************************************************************************
		 ********************************************************************************************************************************
		 ****** 2. subDirList : create HashMap(fileindex, filename) reading all files under target directory    *************************
		 ****** - Param : String source(target directory)********************************************************************************
		 ****** - Return : HashMap<String,String> ***************************************************************************************
		 ******************************************************************************************************************************** 
		 ********************************************************************************************************************************/
            public void indexDirList(String source, HashMap<String, String> index) throws IOException
            {
                    File dir = new File(source);
                    if(findex.containsValue(dir.getAbsolutePath()))
                    {
                            System.out.println(dir.getAbsolutePath() + " is already present in the database.");
                            return;
                    }
                    
                    if(dir.isDirectory()){
                            File[] fileList = dir.listFiles();
                            for(int i = 0 ; i < fileList.length ; i++){
                                    indexDirList(fileList[i].getCanonicalPath().toString(),index);
                            }
                    }
                    else if(dir.isFile())
                    {
                            boolean unique = false;
                            int fileIndex=0;
                            while(!unique)
                            {
                                 fileIndex = (int)(Math.random()*1000000000);
                                 if(!findex.containsKey(Integer.toString(fileIndex)) && fileIndex != 0){
                                    unique = true;
                                 }
                                 if(unique)
                                 {
                                    findex.put(Integer.toString(fileIndex), dir.getAbsolutePath());
                                    index.put(Integer.toString(fileIndex), dir.getAbsolutePath());
                                 }
                            }
                    }
            }
		
            public HashMap<String, String> addFileMap(String[] source){
                    HashMap<String, String> index = new HashMap<String, String>();
                    try{
                            for(int i=0;i<source.length;i++){
                                    File file = new File(source[i]);
                                    if(!file.exists())
                                    {
                                        System.out.println("The file " + source[i] + " does not exist.");
                                        continue;
                                    }
                                    indexDirList(source[i],index);
                            }
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                    return index;
            }
                
	    /********************************************************************************************************************************
	     ********************************************************************************************************************************
	     ****** 4. searchKey : search Keyword to return Fileindex which contains Keyword ************************************************
	     ****** - Param : String keyword                                                 ************************************************
	     ****** - Return : Fileindex(Success), No result(Fail)                           ************************************************
	     ******************************************************************************************************************************** 
	     ********************************************************************************************************************************/
	    public String[][] searchKey(String keyword) throws FileNotFoundException, IOException
	    {
                        File file = new File(wordIndexFile);
                        if(!file.exists() || !file.isFile())
                        {
                            System.out.println("Index file doesn't exist. Exiting.");
                            System.exit(0);
                        }
                        
                        file = new File(fileMapFile);
                        if(!file.exists() || !file.isFile())
                        {
                            System.out.println("File mapping file doesn't exist. Exiting.");
                            System.exit(0);
                        }
                        
                        findex = readHashMap(fileMapFile);
                        kindex = readHashMap(wordIndexFile);
                
			FileReader fr = new FileReader(wordIndexFile);
			BufferedReader br = new BufferedReader(fr);
			String line = "";
			String[] target = null;
                        String[] files = null;
			String[][] result = null;
			
			while((line = br.readLine()) != null)
			{
				int index = line.indexOf("|");
				String com_line = line.substring(0, index);
				if(com_line.equalsIgnoreCase(keyword))
				{
					target = line.split("\\|");
					break;
				}
			}
			if(target!=null){
                            files = target[1].split("\\^");
                            result = new String[files.length][2];
                            for(int i=0; i<files.length; i++)
                            {
                                result[i][0] = files[i];
                                result[i][1] = findex.get(files[i]);
                            }
                        }
			return result;
	    }
	    /********************************************************************************************************************************
	     ********************************************************************************************************************************
	     ****** 5. createKeywordMap(String[] filenames) : create a HashMap(keyword, fileindex) ******************************************
	     ****** - Param : FileReader fin,int i,int fileindex,HashMap<String,String> kindex     ******************************************
	     ****** - Return : HashMap<String,String>                                              ******************************************
	     ******************************************************************************************************************************** 
	     ********************************************************************************************************************************/
	    public void addKeywordMap(int fileindex) throws IOException
	    {
                    FileReader fr = new FileReader(findex.get(Integer.toString(fileindex)));
                    BufferedReader br = new BufferedReader(fr);

                    String line;
                    while((line = br.readLine()) != null)
                    {
                            //String[] filter_word = {" ","\\/","-","\\.","\\?","\\~","\\!","\\@","\\#","\\$","\\%","\\^","\\&","\\*","\\(","\\)","\\_","\\+","\\=","\\|","\\}","\\]","\\{","\\[","\"","\\'","\\:","\\;","\\<","\\>","\\\\"};
                            String[] filter_word = {" ","\\/","\\?","\\~","\\@","\\#",
                                "\\$","\\%","\\^","\\&","\\*","\\(","\\)","\\_","\\+",
                                "\\=","\\|","\\}","\\]","\\{","\\[","\"","\\'","\\:",
                                "\\;","\\<","\\>","\\\\","\\n","\\t","\\r","\\?","\\."};
                            line = line.toLowerCase();
                            for(int k=0;k<filter_word.length;k++)
                            {
                                    //Substitute special characters with ','
                                    line = line.replaceAll(filter_word[k],",");
                            }
                            //Split the sentence based on special characters
                            String[] keyword = line.split(",");

                            for(int j=0;j<keyword.length;j++)
                            {
                                    if(keyword[j].length()==0){continue;}
                                    if(kindex.containsKey(keyword[j]))
                                    {
                                            String[] fileList = kindex.get(keyword[j]).split("\\^");
                                            boolean flag = false;
                                            for(int i=0;i<fileList.length;i++){
                                                    if(fileList[i].equals(Integer.toString(fileindex)))
                                                    {
                                                        flag = true;
                                                    }
                                            }
                                            if(!flag)
                                            {
                                                kindex.put(keyword[j], kindex.get(keyword[j])+"^"+Integer.toString(fileindex));
                                            }
                                    }
                                    else
                                    {
                                            kindex.put(keyword[j], Integer.toString(fileindex));
                                    }
                            }
                    }
	    }
            
            public HashMap<String, String> readHashMap(String filename) throws IOException
	    {
                HashMap<String, String> index = new HashMap<String, String>();
                File file = new File(filename);
                if(file.exists() && file.isFile())
                {
                    FileReader fis = new FileReader(filename);
                    BufferedReader br = new BufferedReader(fis);
                    String line;
                    String[] target;
                    while((line = br.readLine()) != null)
                    {
                         target = line.split("\\|");
                         index.put(target[0], target[1]);
                    }
                }
                return index;
	    }
            
            public void writeHashMap(HashMap<String, String> index, String filename) throws IOException
            {
                    FileWriter fos = new FileWriter(filename);
                   
                    Set keySet = index.keySet();
                    Iterator Ir_keySet = keySet.iterator();
                    while (Ir_keySet.hasNext())
                    {
                            String keyword = Ir_keySet.next().toString();
                            String output = keyword+"|"+index.get(keyword)+"|\n";
                            fos.write(output);
                    }
                    fos.close();
            }
            
            public void deleteKeywordMap(int fileindex) throws IOException
	    {
                    FileReader fr = new FileReader(findex.get(Integer.toString(fileindex)));
                    BufferedReader br = new BufferedReader(fr);

                    String line;
                    while((line = br.readLine()) != null)
                    {
                            //String[] filter_word = {" ","\\/","-","\\.","\\?","\\~","\\!","\\@","\\#","\\$","\\%","\\^","\\&","\\*","\\(","\\)","\\_","\\+","\\=","\\|","\\}","\\]","\\{","\\[","\"","\\'","\\:","\\;","\\<","\\>","\\\\"};
                            String[] filter_word = {" ","\\/","\\?","\\~","\\@","\\#",
                                "\\$","\\%","\\^","\\&","\\*","\\(","\\)","\\_","\\+",
                                "\\=","\\|","\\}","\\]","\\{","\\[","\"","\\'","\\:",
                                "\\;","\\<","\\>","\\\\","\\n","\\t","\\r","\\?","\\."};
                            line = line.toLowerCase();
                            for(int k=0;k<filter_word.length;k++)
                            {
                                    //Substitute special characters with ','
                                    line = line.replaceAll(filter_word[k],",");
                            }
                            //Split the sentence based on special characters
                            String[] keyword = line.split(",");

                            for(int j=0;j<keyword.length;j++)
                            {
                                    if(keyword[j].length()==0){continue;}
                                    if(kindex.containsKey(keyword[j]))
                                    {
                                            String[] fileList = kindex.get(keyword[j]).split("\\^");
                                            if(fileList.length == 1 && fileList[0].equals(Integer.toString(fileindex)))
                                            {
                                                    kindex.remove(keyword[j]);
                                                    continue;
                                            }
                                            
                                            String indexStr="";
                                            int k=0;
                                            for(int i=0;i<fileList.length;i++)
                                            {       
                                                    if(!fileList[i].equals(Integer.toString(fileindex)))
                                                    {
                                                            if(k==0)
                                                            {
                                                                    indexStr = fileList[i];
                                                                    ++k;
                                                            }
                                                            else
                                                            {
                                                                    indexStr = indexStr + "^" + fileList[i];
                                                            }
                                                    }
                                            }
                                            kindex.put(keyword[j], indexStr);
                                    }
                            }
                    }
	    }
            
            
            public int[] deleteFiles(String[] fileList) throws IOException
            {
                    
                
					
                    int[] fileNums = new int[fileList.length];
					
				
                    findex = readHashMap(fileMapFile);
                    kindex = readHashMap(wordIndexFile);
                    
                    int key = 0;
                    
                    for(int i=0;i<fileList.length;i++)
                    {
                            key = 0;
                            File file = new File(fileList[i]);
                            
                            if(!file.exists())
                            {
                                    System.out.println("The file " + fileList[i] + " does not exist.");
                                    continue;
                            }
                            
                            String filePathList = file.getAbsolutePath();
                            if(findex.containsValue(filePathList))
                            {
                                    Set fileKeySet = findex.keySet();
                                    Iterator Ir_fileKeySet = fileKeySet.iterator();
                                    while (Ir_fileKeySet.hasNext()) 
                                    {
                                            String keyword = Ir_fileKeySet.next().toString();
                                            if(findex.get(keyword).equals(filePathList))
                                            {
                                                    key = Integer.valueOf(keyword);
                                                    break;
                                            }
                                    }
                            }
                            
                            if(key==0)
                            {
                                    System.out.println("The file " + fileList[i] + " is not present on the server.");
                                    continue;
                            }
							else
								fileNums[i] = key;
								
                            deleteKeywordMap(key);
                            findex.remove(String.valueOf(key));
                    }
                    
                    writeHashMap(findex,fileMapFile);
                    writeHashMap(kindex,wordIndexFile);                    
                    return fileNums;
            }
	} //End class BuildIndex

}//End class mClient

