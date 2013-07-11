package com.ling.remoteservice;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import com.ling.remoteservice.annonation.Local;

public class ServiceClassLoader {
	String extPath = "/ext";
	String libPath = "/lib";
	String configPath = "/config";
	String baseDir = ".";
	File extDir;
	File libDir;
	static Log logger = LogFactory.getLog(ServiceClassLoader.class);
	ClassLoader libJarLoader;
	ClassLoader serviceJarLoader;
	URL[] serviceJarUrls;
	
	public ServiceClassLoader(){	
		setPath();
		loadJars();
	}
	//private static final Object INSTRUMENTATION_KEY = UUID.fromString("214ac54a-60a5-417e-b3b8-772e80a16667");
	@SuppressWarnings("rawtypes")
	public Map<Local, Class<?>> getServiceClasses(){
		try{
			Reflections reflections  = ConfigurationBuilder.build(serviceJarLoader,serviceJarUrls).build();
			
			Set<Class<?>> classes=reflections.getTypesAnnotatedWith(Local.class);
			
			Map<Local,Class<?>> servicelist=new HashMap<Local, Class<?>>(); 
			//Field f = ClassLoader.class.getDeclaredField("classes");
			//f.setAccessible(true);
			//Vector<Class> classes =  (Vector<Class>) f.get(serviceJarLoader);
			logger.error("classes list:"+ classes.size());
			for (Class clazz: classes){
				Local loc=((Class<?>)clazz).getAnnotation(Local.class); 
                if (loc!=null){
                	logger.error("Classloader ["+clazz.getClassLoader().hashCode()+"] Service class:"+clazz);
                	servicelist.put(loc, clazz);
                }
			}
			logger.error("Service Classes: "+servicelist);
			return servicelist;
		}catch(Exception e){
			logger.error("",e);
		}
		return null;
	}
	
	private void loadJars() {
        if (libDir.exists()) {
            File[] jars = libDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".jar"))
                        return true;
                    return false;
                }
            });
            if (jars.length==0){
            	logger.error("Cannot find any lib jar in "+libDir.getAbsolutePath());
            }else{
	            URL[] fileurl = new URL[jars.length];
	            //if (fileurl.length==0) 
	            	
	            for (int idx=0;idx<jars.length; idx++) {
	            	File jarFile=jars[idx];
	            	try {
						fileurl[idx] = jarFile.toURI().toURL();
						logger.info("add jar url:"+fileurl[idx].toString());
					} catch (MalformedURLException e) {
						logger.error("",e);
					}
	            }
	            libJarLoader = new URLClassLoader(fileurl, getClass().getClassLoader());
            }
            
        }
        if (libJarLoader==null)
        	libJarLoader = getClass().getClassLoader();
        if (extDir.exists()) {
            File[] jars = extDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".jar"))
                        return true;
                    return false;
                }
            });
            if (jars.length==0){
            	logger.error("Cannot find any ext jar in "+extDir.getAbsolutePath());
            }else{
            	//serviceJars = new String[jars.length];
            	serviceJarUrls = new URL[jars.length];
	            for (int idx=0;idx<jars.length; idx++) {
	            	File jarFile=jars[idx];
	            	try {
	            		serviceJarUrls[idx] = jarFile.toURI().toURL();
						logger.info("add jar url:"+serviceJarUrls[idx].toString());
					} catch (MalformedURLException e) {
						logger.error("",e);
					}
	            }
	            serviceJarLoader = new URLClassLoader(serviceJarUrls, libJarLoader);
	            
	            Thread.currentThread().setContextClassLoader(serviceJarLoader);
            }
        }
        if (serviceJarLoader==null)
        	serviceJarLoader = libJarLoader;
    }
	
	private void setPath() {
        String classfile = getFile(getClass().getName());
        URL url = getClass().getClassLoader().getResource(classfile);
        System.out.println("class url:" + url);
        try {

            String uri = url.toURI().toString();
            // System.out.println(uri);
            if (uri.endsWith(classfile)) {
                String path = uri.substring(0, uri.length() - classfile.length());
                if (path.startsWith("jar:"))
                    path = path.substring(4);
                System.out.println(path);
                int lidx1 = path.lastIndexOf('/');
                if (lidx1 != -1) {
                    int lidx2 = path.lastIndexOf('/', lidx1 - 1);
                    System.out.println(extPath);
                    baseDir = path.substring(0, lidx2);
                    configPath = baseDir + configPath;
                    extPath = baseDir + extPath;
                    libPath = baseDir + libPath;
                    extDir = new File(new URI(extPath));
                    libDir = new File(new URI(libPath));
                    File conffile=new File(new URI(configPath));
                    configPath = conffile.getAbsolutePath();
                    System.out.println("extpath:" + extDir.getAbsolutePath());
                    System.out.println("libpath:" + libDir.getAbsolutePath());
                    System.out.println("configpath:" + configPath);
                    if (!extDir.exists()) {
                        extDir.mkdir();
                    }
                }
            }
            System.out.println("Extentions Dir[" + extDir.getAbsolutePath() + "]");
        } catch (URISyntaxException e) {
            logger.error("", e);
        }
    }
    private String getFile(String name) {
        name = name.replaceAll("\\.", "/");
        return name + ".class";
    }
}
