package com.tencent.mobileqq.dinifly.sample;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class LogWriter {  
    
    private static LogWriter mLogWriter;  
  
    private static String mPath;  
      
    private static Writer mWriter;  
      
      
    private LogWriter(String file_path) {  
        this.mPath = file_path;  
        this.mWriter = null;  
    }  
      
    public static LogWriter open(String file_path) throws IOException {  
        if (mLogWriter == null) {  
            mLogWriter = new LogWriter(file_path);  
        }  
        File mFile = new File(mPath);  
         FileWriter wirter = new FileWriter(mPath);
        mWriter = new BufferedWriter(wirter,2048);  
        return mLogWriter;  
    }  
      
    public void close() throws IOException {  
        mWriter.close();  
    }  
      
    public void print(String log) throws IOException {  
        mWriter.write(log);  
        mWriter.write("\n");  
        mWriter.flush();  
    }  
      
    public void print(Class cls, String log) throws IOException {
        mWriter.write(cls.getSimpleName() + " ");  
        mWriter.write(log);  
        mWriter.write("\n");  
        mWriter.flush();  
    }  
      
}  
