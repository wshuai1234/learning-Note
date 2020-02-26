public class DispatcherServlet extends HttpServlet{
	List<String> classUrls = new ArrayList<String>();
	Map<String, Object> ioc = new HashMap<>();
	Map<String, Object> urlHandlers = new HashMap<>();
	public void init(ServletConfig config) throws ServletException{
		doScanPackage("com.enjoy");
		doInstance();
		doAutowired();
		doUrlMapping();
	}
	//扫描com.enjoy得到所有java class
	//basePackage = "com.enjoy"
	public void doScanPackage(String basePackage){
		URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.","/"));
		String fileStr = url.getFile();
		File file = new File(fileStr);
		String[] fileStr = file.list();
		for (String path : fileStr){
			File filePath = new File(fileStr + path);
			if (filePath.isDirectory()){
				doScanPackage(basePackage + "." + path);
			}else{
				classUrls.add(basePackage + "." + filePath.getName().replace(".class",""));
			}
		}
	}
	public void doInstance(){
		// classUrls // 实例化有特殊注解的类
		for(String classUrl : classUrls){
			// classUrl = com.enjoy.xx.JamesController
			try{
				Class<?> clazz = Class.forName(classUrl); // reflection
				if (clazz.isAnnotationPresent(EnjoyController.class)){
					Object instance = clazz.newInstance();
					// IOC = MAP
					//key 对象首字母小写
					// customize =>  @EnjoyController("/james") 
					//use james as key in ioc map
					EnjoyController map1 = clazz.getAnnotation(EnjoyRequestMapping.class);
					ioc.put(map1.value() ,instance);
				}else if (clazz.isAnnotationPresent(EnjoyService.class)){
					Object instance2 = clazz.newInstance();
					EnjoyService es = clazz.getAnnotation(EnjoyService.class);
					ioc.put(es.value() ,instance2);
				}else{
					continue;
				}

			}catch(ClassNotFoundException e){
				e.printStackTrace();
			}catch(InstantiationException e){
				e.printStackTrace();
			}catch(IllegalAccessException e){
				e.printStackTrace();
			}
			
		}

	}
	public void doAutowired(){
		// ioc map already have beans
		// controller -- @Autowired service
		// Service -- @Autowired Repository
		for (Map.Entry<String, Object> entry : ioc.entrySet()){
			// 判断控制类里面有没有Autowired
			Object instance = entry.getValue();
			Class<?> clazz = instance.getClass();
			if (clazz.isAnnotationPresent(EnjoyController.class)){
				// is controller , check autowired
				Field[] fields = clazz.getDeclaredFields();
				for (Field field : fields){
					if (field.isAnnotationPresent(EnjoyAutowired.class)){
						// found
						EnjoyAutowired ea = field.getAnnotation(EnjoyAutowired.class);
						String key =ea.value();
						Object ins = ioc.get(key); // got the service
						field.setAccessible(true); // 打开private constructor的权限
						try{
							field.set(instance, ins); // 将instance赋值为service
						}catch(IllegalAccessException e){
							e.printStackTrace();
						}
					}
				}
			}else{
				continue;
			}
		}
	}
	public void doUrlMapping(){
		//当前IOC容器中已经放入bean，只有控制类有
		for (Map.Entry<String, Object> entry : ioc.entrySet()){
			// 判断控制类里面有没有Autowired
			Object instance = entry.getValue();
			Class<?> clazz = instance.getClass();
			//判断是不是controller
			if (clazz.isAnnotationPresent(EnjoyController.class)){
				EnjoyRequestMapping map1 = clazz.getAnnotation(EnjoyRequestMapping.class);
				String classpath = map1.value(); // james
				Method[] methods = clazz.getMethods();
				//获取方法路径
				for (Method method : methods){
					if (method.isAnnotationPresent(EnjoyRequestMapping.class)){
						EnjoyReuestMapping map2 = method.getAnnotation(EnjoyReuestMapping.class);
						String methodpath = map2.value(); // /query
						urlHandlers.put(classpath + methodpath, method);
					}else{
						continue;
					}
				}
			}
		}
	}
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		this.doPost(request, response);
	}



	// http://127.0.0.1:8080/mvc/james/query    ---dopost
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		// james/query --method method.invoke()
		String uri = request.getRequestURI(); // -- /mvc/james/query
		String context = request.getContextPath(); //    /mvc
		String path = uri.replace(context, ""); //    path = james/query
		Method method = (Method)urlHandlers.get(path);
		// parameter and its object?
		JamesController instance = (JamesController)ioc.get("/" + path.split("/")[1]);
		Obejct[] args = hand(request, response, method);
		method.invoke(instance, args);
	}
}