package com.ufhmanager;

import java.io.FileOutputStream;
import java.io.IOException;

import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;


import com.dt50.IRfidDevice;
import com.dt50.RFIDCallback;
import com.dt50.RFIDReaderHelper;
import com.dt50.RFIDTagInfo;
import com.dt50.ReaderConnector;
import com.dt50.rxobserver.RXObserver;
import com.dt50.rxobserver.ReaderSetting;
import com.dt50.rxobserver.bean.RXInventoryTag;
import com.dt50.rxobserver.bean.RXOperationTag;
import com.module.interaction.ModuleConnector;

import com.rfid.RFIDReaderHelper;
import com.rfid.ReaderConnector;
import com.util.StringTool;

/**
 * 参考demo
 */
public class RfidDevice implements IRfidDevice {
	private ModuleConnector connector = new ReaderConnector();
	private RFIDReaderHelper mReader;
	private ReaderSetting mseting;
	private String data;
	private RFIDTagInfo mRFIDTagInfo;
	private volatile Thread mThread=null;
	private volatile boolean mWorking = true; 
	private boolean loopstate=false;
	private RFIDCallback mRFIDCallback;
	private  int retepc=0;
	private  boolean retuser=false;
	private  int rettid_epc=0;
	long oldtime,newtime;
   
	 //private static final SoundPool mSoundPool =  new SoundPool(1, AudioManager.STREAM_MUSIC,5);
	 int heightBeepId;
	/**
     * 初始化设备
     * @return 成功后返回true, 失败返回false
     * */
    public boolean init(){
	   	 boolean initstate=set53CGPIOEnabled(true);
	   	//heightBeepId = mSoundPool.load("/etc/Scan_new.ogg", 1);
	   //	heightBeepId =mSoundPool.load(getApplicationContext(), R.raw.beeper_short,1);
	   	 SystemClock.sleep(200);
	   	 return initstate;
   }

    /**
     * 释放设备资源
     * @return 成功后返回true, 失败返回false
     * */
    public boolean free(){
    	mWorking=false;
    	loopstate=false;
    	mThread=null;
   	 boolean freestate=set53CGPIOEnabled(false);
   	 connector.disConnect();
   	 return freestate;
   }


    /**
     * 打开设备，如果初始化设备之后RFID功能就可以使用，这个方法返回设备初始化的值
     * @return 成功后返回true, 失败返回false
     * */
    public boolean open(){
    	String Serial_port="/dev/ttyHSL0";
    	if(connector.connectCom(Serial_port, 115200)){
    		try {
				mReader = RFIDReaderHelper.getDefaultHelper();
				mseting = ReaderSetting.newInstance();
				mseting.btReadId=(byte)0xFF;
				mReader.registerObserver(rxObserver);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
    	}
    	if(mReader!=null){
    		return true;
    	}else{
    		return false;
    	}
    }

    /**
     * 判断该RFID是否可用
     * @return true, 此时设备可以使用读写功能， false 此时设备不可以使用读写功能
     **/
    public boolean isOpen(){

    	return connector.isConnected();
    	
    }

    /**
     * 关闭设备
     * @return true 关闭成功， false 关闭失败
     * */
    public boolean close(){
    	connector.disConnect();
    	return true;
    }
    /**
     * RFID单次扫描
     * @return 返回EPC的值
     */
    public RFIDTagInfo singleScan(){
    	mRFIDTagInfo=null;
    	mReader.realTimeInventory(mseting.btReadId,(byte)0x01);
    	SystemClock.sleep(200);
    	if(mRFIDTagInfo!=null){
    		return  mRFIDTagInfo;
    	}else{
    		mRFIDTagInfo=new RFIDTagInfo();
    		mRFIDTagInfo.optimizedRSSI=0;
    		mRFIDTagInfo.epcID="";
    		return mRFIDTagInfo;
    	}
    }

    /**
     * 开始连续扫描
     * */
    public void startScan(RFIDCallback tagCallback){
    	mRFIDCallback = tagCallback;
    	mRFIDTagInfo=null;
		stopScan();

    	if(mThread==null)
		{
			mWorking=true;
			loopstate=true;
			mThread = new Thread(new Runnable() {  
	        @Override  
	        public void run() {  
	        	Log.i("urovo","mWorking:"+mWorking);
	        	
	            while(mWorking){
	            	Log.i("urovo","loopstate:"+loopstate);
	            		if(loopstate){
                            newtime =oldtime=System.currentTimeMillis();
							mReader.customizedSessionTargetInventory(mseting.btReadId,(byte)0x00,(byte)0x00,(byte)0x01);
	            			//mReader.realTimeInventory(mseting.btReadId,(byte)0x01);
	            			loopstate=false;
	            		}{
                            newtime=System.currentTimeMillis();
							Log.i("urovo","newtime-oldtime:"+(newtime-oldtime));
	            		    if(newtime-oldtime>500) {
                                oldtime = newtime;
                                loopstate = true;
                            }
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
	            		}
	            	}
	        	}
	        });
			mThread.start();
		}
    	
    }

    /**
     * 关闭连续扫描
     * */
    public void stopScan(){
    	if(mThread!=null)
		{
    		loopstate=false;
			mWorking=false;
			mThread=null;
		}
    }
    	
    

    /**
     * 对EPC进行写入
     * @param content 写入到EPC的内容
     * */
        public int write(String content){
        	retepc=-1;
			if(TextUtils.isEmpty(content))
				return -1;
			RFIDTagInfo seRFIDTagInfo=singleScan();
			for(int i=0;i<3;i++) {

				String epc=seRFIDTagInfo.epcID;
				if(TextUtils.isEmpty(epc)){
					seRFIDTagInfo=singleScan();
				}else{
					String[] epcresult = StringTool.stringToStringArray(epc, 2);
					byte[] btAryepc= StringTool.stringArrayToByteArray(epcresult, epcresult.length);
					byte btEpcLen = (byte) btAryepc.length;
					mReader.setAccessEpcMatch(mseting.btReadId, btEpcLen,btAryepc);
					Log.d(TAG,"Selection -----epc :"+epc);
					i=5;
				}
			}

        	int Remainder=(byte) (content.length()%4);
			switch (Remainder) {
				case 1:
					content=content+"000";
					break;
				case 2:
					content=content+"00";
					break;
				case 3:
					content=content+"0";
					break;
			}
			byte len=(byte) ((content.length()/4)+1);
        	String[] result = StringTool.stringToStringArray("00000000", 2);
        	byte[] btAryPassWord = StringTool.stringArrayToByteArray(result, 4);
			String two = Integer.toBinaryString((content.length()/4));
			int twolen=two.length();
			switch (twolen) {
				case 1:
					two="0000"+two+"000";
					break;
				case 2:
					two="000"+two+"000";
					break;
				case 3:
					two="00"+two+"000";
					break;
				case 4:
					two="0"+two+"000";
					break;
				case 5:
					two=two+"000";
					break;

			}
			Log.d(TAG,"write--new-two:"+two);

			int ten = Integer.parseInt(two, 2);
			String sixteen = Integer.toHexString(ten);
			int sixteenlen=sixteen.length();
			switch (sixteenlen) {
				case 1:
					sixteen="0"+sixteen+"00";
					break;
				case 2:
					sixteen=sixteen+"00";
					break;

			}
			content=sixteen+content;
			Log.d(TAG,"write--sixteen:"+sixteen+"len:"+len);
			Log.d(TAG,"new content:"+content);
			for(int j=0;j<3;j++) {
				mReader.writeTag(mseting.btReadId,btAryPassWord,(byte)0x01,(byte)0x01,len,StringTool.hexStringToBytes(content));
				SystemClock.sleep(200);
				if(retepc==0){
					j=5;
				}
			}
        	//mReader.writeTag(mseting.btReadId,btAryPassWord,(byte)0x01,(byte)0x01,len,StringTool.hexStringToBytes(content));
			mReader.cancelAccessEpcMatch(mseting.btReadId);
        	Log.d(TAG,"write-----end");
        	//SystemClock.sleep(200);
        	return    retepc;
        }

    /**
     * 设置功率
     * @param power 0~30的整数
     * */
        public boolean setPower(int power){
        	byte btOutputPower = (byte)power;
        	int ret =mReader.setTemporaryOutputPower(mseting.btReadId, btOutputPower);
        	return ret==0?true:false;
        }



    /**
     * 读取数据
     * @param bank  数据所在区域，如EPC TID USER RESERVED，
     * @return 读取到的内容
     */
        public String readData(RFIDAreaEnum bank){
        	byte btMemBank = 0;
    		switch (bank){
            case  RESERVED :btMemBank = (byte)0x00;
				mReader.readTag(mseting.btReadId, btMemBank,(byte) 0x02,(byte) 0x06,"".getBytes());
                break;
            case EPC:btMemBank = (byte)0x01;
				mReader.readTag(mseting.btReadId, btMemBank,(byte) 0x02,(byte) 0x06,"".getBytes());
                break;
            case TID :btMemBank = (byte)0x02;
				mReader.readTag(mseting.btReadId, btMemBank,(byte) 0x00,(byte) 0x06,"".getBytes());
                break;
            case USER:btMemBank = (byte)0x03;
				mReader.readTag(mseting.btReadId, btMemBank,(byte) 0x02,(byte) 0x06,"".getBytes());
                break;
    	}
        	

        	SystemClock.sleep(200);
        	return  data;
        }

    /**
     * 根据tid，写入epc数据
     * @param tid 指定要写入的RFID标签的TID
     * @param content 要写入的内容
     */
        public int write(String tid, String content){
        	rettid_epc=-1;
			if(TextUtils.isEmpty(content) || TextUtils.isEmpty(tid) )
				return -1;
			int TIDlen= (tid.length()/2);
			int  startaddress =0;
			if(TIDlen!=12){
				startaddress=((12-TIDlen)*8);
			}
			int Masklen=TIDlen*8;
        	byte[] maskValue=StringTool.hexStringToBytes(tid);
			mReader.setTagMask((byte)0xff,(byte) 0x01, (byte)0x00,(byte) 0x00, (byte)0x02, (byte)startaddress, (byte)Masklen,maskValue);
			SystemClock.sleep(200);
			pcwrite(content);
			//int ret=mReader.writeTag(mseting.btReadId,btAryPassWord,(byte)0x01,(byte)0x02,len,StringTool.hexStringToBytes(content));
			SystemClock.sleep(200);
			mReader.clearTagMask((byte)0xff,(byte) 0x01);
        	return rettid_epc;
        }

	public int pcwrite(String content){
		retepc=-1;
		if(TextUtils.isEmpty(content))
			return -1;
		int Remainder=(byte) (content.length()%4);
		switch (Remainder) {
			case 1:
				content=content+"000";
				break;
			case 2:
				content=content+"00";
				break;
			case 3:
				content=content+"0";
				break;
		}
		byte len=(byte) ((content.length()/4)+1);
		String[] result = StringTool.stringToStringArray("00000000", 2);
		byte[] btAryPassWord = StringTool.stringArrayToByteArray(result, 4);
		String two = Integer.toBinaryString((content.length()/4));
		int twolen=two.length();
		switch (twolen) {
			case 1:
				two="0000"+two+"000";
				break;
			case 2:
				two="000"+two+"000";
				break;
			case 3:
				two="00"+two+"000";
				break;
			case 4:
				two="0"+two+"000";
				break;
			case 5:
				two=two+"000";
				break;

		}


		int ten = Integer.parseInt(two, 2);
		String sixteen = Integer.toHexString(ten);
		int sixteenlen=sixteen.length();
		switch (sixteenlen) {
			case 1:
				sixteen="0"+sixteen+"00";
				break;
			case 2:
				sixteen=sixteen+"00";
				break;

		}
		content=sixteen+content;

		for(int j=0;j<3;j++) {
			mReader.writeTag(mseting.btReadId,btAryPassWord,(byte)0x01,(byte)0x01,len,StringTool.hexStringToBytes(content));
			SystemClock.sleep(200);
			if(retepc==0){
				j=5;
			}
		}
		//mReader.writeTag(mseting.btReadId,btAryPassWord,(byte)0x01,(byte)0x01,len,StringTool.hexStringToBytes(content));

		return    retepc;
	}



	/**
     * 往USER去区写入数据
     * */
       public boolean writeUser(String content){
       		retuser=false;
		    if(TextUtils.isEmpty(content))
		   		return false;
        	byte len=(byte) (content.length()/4);
        	String[] result = StringTool.stringToStringArray("00000000", 2);
        	byte[] btAryPassWord = StringTool.stringArrayToByteArray(result, 4);
        	mReader.writeTag(mseting.btReadId,"".getBytes(),(byte)0x03,(byte)0x02,len,StringTool.hexStringToBytes(content));
        	 SystemClock.sleep(200);
        	return retuser;
        }


	
	
	
	

    private boolean set53CGPIOEnabled(boolean enable){
	    FileOutputStream f = null;
	    FileOutputStream f1 = null;
	    try{
	    	Log.i("urovo","enable:"+enable);
	    	 f = new FileOutputStream("/sys/devices/soc/soc:sectrl/ugp_ctrl/gp_pogo_5v_ctrl/enable");
		     f.write(enable?"1".getBytes():"0".getBytes());
		     f1 = new FileOutputStream("/sys/devices/soc/soc:sectrl/ugp_ctrl/gp_otg_en_ctrl/enable");
		     f1.write(enable?"1".getBytes():"0".getBytes());
		      return  true; 
	    }catch (Exception e){ 
	        e.printStackTrace();
	        return  false;
	    }finally {
	        if(f != null){
	            try {
	                f.close();
	                f1.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
	    }
	}
    
    String TAG="urovo";
    RXObserver rxObserver = new RXObserver(){
    	
        @Override
        protected void onInventoryTag(RXInventoryTag tag) {
            Log.d(TAG,"onInventoryTag");
           // mSoundPool.play(heightBeepId, 1, 1, 0, 0, 1);
            loopstate=true;
            Log.d(TAG,"RSSI : " + (Integer.parseInt(tag.strRSSI) ) + "dBm");
            mRFIDTagInfo=new RFIDTagInfo();
            mRFIDTagInfo.epcID=tag.strEPC.replace(" ", "");
			mRFIDTagInfo.tid="0000";
            int rssi=Integer.parseInt(tag.strRSSI);
            if(rssi>100)rssi=100;
			if(rssi<0) rssi=0;
            mRFIDTagInfo.optimizedRSSI=(rssi);
            if(mRFIDCallback!=null)
            	mRFIDCallback.onResponse(mRFIDTagInfo);
        }

           @Override
           protected void onInventoryTagEnd(RXInventoryTag.RXInventoryTagEnd endTag) {
               Log.d(TAG,"onInventoryTagEnd");
               loopstate=true;
              // mReader.realTimeInventory((byte) 0xff,(byte)0x01);
           }
           
 

           @Override
           protected  void onOperationTag(RXOperationTag tag) {
               Log.d(TAG,"onOperationTag");
               retepc=0;
               retuser=true;
               rettid_epc=0;
               data=tag.strData.replace(" ", "");
              // mSoundPool.play(heightBeepId, 1, 1, 0, 0, 1);
           }

           @Override
           protected void onOperationTagEnd(int operationTagCount) {
               Log.d(TAG,"onOperationTagEnd  :  " + operationTagCount);
           }

           @Override
           protected void refreshSetting(ReaderSetting readerSetting) {
               Log.d(TAG,"refreshSetting");

           }
           protected void onExeCMDStatus(byte cmd,byte status){
        	   Log.d(TAG,"mRFIDCallback:"+mRFIDCallback);
        	   if(mRFIDCallback!=null)
               	mRFIDCallback.onError(status);
           }
           
       };




}
