package com.ling.remoteservice.utils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TypeUtils {
	static Log logger=LogFactory.getLog(TypeUtils.class);
	
	public static boolean parameterMatch(Class<?>[] pts, Class<?>[] ipts) {
		boolean mmatch = true;
		if (pts.length > 0) {

			for (int i = 0; i < pts.length; i++) {
				// System.out.println("  params:"+pts[i]+" --> "+ipts[i]);
				boolean pmatch = false;
				if (pts[i] != ipts[i]) { // 类不相同
					pmatch = typeMatch(pts[i], ipts[i]);
				} else {
					// System.out.println("      params:"+pts[i]+" == "+ipts[i]);
					pmatch = true;
				}
				if (!pmatch) {
					mmatch = false;
					break;
				}
			}
		}
		return mmatch;
	}

	public static boolean typeMatch(Class<?> class1, Class<?> class2) {
		if (class1 == int.class)
			class1 = Integer.class;
		if (class1 == long.class)
			class1 = Long.class;
		if (class1 == boolean.class)
			class1 = Boolean.class;

		if (class2 == int.class)
			class2 = Integer.class;
		if (class2 == long.class)
			class2 = Long.class;
		if (class2 == boolean.class)
			class2 = Boolean.class;

		if (class1 == class2)
			return true;
		// 判断是否兼容
		boolean pmatch = false;
		java.lang.reflect.Type[] classInterfs = class1.getInterfaces();
		// boolean match=false;
		for (java.lang.reflect.Type tt : classInterfs) {
			if (tt == class2) {// 判断类兼容？
				// System.out.println("      ----interface:"+class1+"<--"+tt+" == "+class2);
				pmatch = true;
				break;
			} else {
				// System.out.println("      ----interface:"+class1+"<--"+tt+" != "+class2);
				pmatch = typeMatch((Class) tt, class2);
				if (pmatch)
					break;
			}
		}
		if (!pmatch) {
			java.lang.reflect.Type t = class1.getSuperclass();
			if (t == class2)
				pmatch = true;
			else if (t != null)
				pmatch = typeMatch((Class) t, class2);
			// System.out.println("      ----superclass:"+class1+"<--"+t+" != "+class2);
		}
		return pmatch;
	}
	

	public static String getProperties(Object object, String prop) {
		if (object == null)
			return "null";
		if (prop == null || prop.length() < 2)
			return object.toString();
		prop = prop.substring(1);
		try {
			if ("class".equals(prop)) {
				return object.getClass().toString();
			} else if (object instanceof Map) {
				return ((Map<?, ?>) object).get(prop).toString();
			} else if (object instanceof List){
				if (prop.matches("\\d+")){
					return ((List<?>)object).get(Integer.valueOf(prop)).toString();
				}
				return null;
			}else {
				try {
					Field f = object.getClass().getDeclaredField(prop);
					f.setAccessible(true);
					Object fieldValue = f.get(object);
					if (fieldValue != null)
						return fieldValue.toString();
					else {
						logger.info("Null [" + prop + "] properties for object[" + object.toString() + "]");
						return "null";
					}
				} catch (NoSuchFieldException e1) {
					logger.error("Can't get field [" + prop + "] for class [" + object.getClass().getName() + "]");
					String mname = "get" + prop.substring(0, 1).toUpperCase() + (prop.length() > 1 ? prop.substring(1) : "");
					try {
						Method m = object.getClass().getDeclaredMethod(mname, null);
						if (m != null) {
							Object fieldValue = m.invoke(object, null);
							if (fieldValue != null)
								return fieldValue.toString();
							else
								return "null";
						} else {
							logger.error("Can't find method [" + mname + "] for class [" + object.getClass().getName() + "]");
						}
					} catch (NoSuchMethodException e) {
						logger.error("Can't find method [" + mname + "] for class [" + object.getClass().getName() + "]");
					} catch (InvocationTargetException e) {
						logger.error("Can't execute method [" + mname + "] for class [" + object.getClass().getName() + "]");
					}
					return "null";
				}
			}
		} catch (SecurityException e) {
			logger.error(object + "." + prop, e);
		} catch (IllegalArgumentException e) {
			logger.error(object + "." + prop, e);
		} catch (IllegalAccessException e) {
			logger.error(object + "." + prop, e);
		}
		return null;
	}
	
    private String getTypesArrayString(Class<?>... types) {
        StringBuffer res=new StringBuffer();
        for (Class<?> t:types){
            res.append(",").append(t.getName());
        }
        return res.toString();
    }
    private Class<?>[] getParamTypes(Object[] args) {
        Class<?>[] pts=new Class<?>[args.length];
        for (int i=0;i<args.length;i++){
            Object o=args[i];
            pts[i]=o.getClass();
        }
        return pts;
    }
}
