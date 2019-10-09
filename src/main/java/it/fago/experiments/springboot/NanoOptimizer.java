package it.fago.experiments.springboot;

import static java.lang.management.ManagementFactory.getMemoryMXBean;
import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.get;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcomes;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 
 * @author Stefano Fago
 *
 */
public class NanoOptimizer {
	//
	private static Logger logger = LoggerFactory.getLogger(NanoOptimizer.class);
	//
	private static final String LINE_SEPARTOR = System.lineSeparator();
	//
	private static final String LAZY_PROC_TEMPLATE = registerLazyProcTemplate();
	//
	private static final String OS = getOperatingSystemMXBean().getName();
	//
	private static final long FACTOR = 1 << 20;
	//
	private String id;
	//
	private ConfigurableApplicationContext context;
	//
	private long min = 256;
	private long max = 512;
	private long maxRam = 768;
	private int threadStack = 256;
	//
	private String configLocation = "classpath:/application.properties";
	//
	private boolean useDefaultMemory;
	//
	private boolean usePreTouch;
	//
	private String lazyProcessor = LAZY_PROC_TEMPLATE;
	//
	private Consumer<String> innerOutput;
	//
	private StringBuilder outBuffer = new StringBuilder(15000);

	// ===========================================================================================
	//
	//
	//
	// ===========================================================================================

	/**
	 * 
	 * @param id
	 * @param context
	 * @return
	 */
	public static NanoOptimizer optimizerFor(String id, ConfigurableApplicationContext context) {
		requireNonNull(id, "id is required...");
		requireNonNull(context, "context is required...");
		return new NanoOptimizer(id, context);
	}

	// ===========================================================================================
	//
	//
	//
	// ===========================================================================================

	private NanoOptimizer(String id, ConfigurableApplicationContext context) {
		this.id = id;
		this.context = context;
	}

	/**
	 * 
	 * @return
	 */
	public NanoOptimizer useDefaultForMemory() {
		this.useDefaultMemory = true;
		return this;
	}

	/**
	 * 
	 * @param usingPreTouch
	 * @return
	 */
	public NanoOptimizer generateJavaCmdLine(boolean usingPreTouch) {
		this.usePreTouch = usingPreTouch;
		return this;
	}

	/**
	 * 
	 * @param usingPreTouch
	 * @param configurationLocation
	 * @return
	 */
	public NanoOptimizer generateJavaCmdLine(boolean usingPreTouch, String configurationLocation) {
		this.configLocation = requireNonNull(configurationLocation, "location parameter needed");
		this.usePreTouch = usingPreTouch;
		return this;
	}

	/**
	 * 
	 * @param packageName
	 * @return
	 */
	public NanoOptimizer generateLazyProcessor(String packageName) {
		this.lazyProcessor = this.lazyProcessor.replace("{0}", packageName);
		return this;
	}

	
	
	/**
	 * 
	 */
	public void build() {
		build(line -> outBuffer.append(line).append(LINE_SEPARTOR));
		logger.info(outBuffer.toString());
		outBuffer.setLength(0);
	}

	/**
	 * 
	 * @param filePath
	 * @param fileSuffix
	 */
	public void build(String filePath, String fileSuffix) {
		requireNonNull(filePath, "a folder is needed");
		requireNonNull(fileSuffix, "at least a void suffix is needed");

		try {
			Path parents = Files.createDirectories(Paths.get(filePath));
			File target = new File(parents.toFile(), id + "_REPORT_" + fileSuffix);
			logger.info("The report will be on the file: {} ",target);
			build(line -> outBuffer.append(line).append(LINE_SEPARTOR));
			RandomAccessFile raf = new RandomAccessFile(target, "rw");
			raf.write(outBuffer.toString().getBytes("UTF-8"));
			raf.close();
		} catch (IOException e) {
			logger.error("Problem writing on file...",e);
			return;
		} finally {
			outBuffer.setLength(0);
		}
	}

	// ===========================================================================================
	//
	//
	//
	// ===========================================================================================

	
	protected void build(Consumer<String> out) {

		innerOutput = out;

		header(id);

		section("MEMORY USAGE & COMMAND LINE");
		showHeapUsage();

		if (!useDefaultMemory) {
			memoryManagement();
		}
		section("JVM COMMAND LINE");
		innerOutput.accept(cmdLine(min, max, maxRam, threadStack, configLocation, usePreTouch));

		section("EARLY IMPORT");
		generateImportAnnotation();

		section("LAZY INITIALIZATION");
		innerOutput.accept(lazyProcessor);

		section("APPLICATION PROPERTIES");
		innerOutput.accept(createApplicationProperties());

		footer();
	}

	
	private void generateImportAnnotation() {
		StringBuilder buffer = openBuffer();

		      get(context.getBeanFactory())
		        .getConditionAndOutcomesBySource()
		        .entrySet()
		        .stream()
		             .filter(fullMatchNoInner())
		             .map(Map.Entry::getKey)
		             .collect(toSet())
		        .stream()
		     .forEach(appendCsv(buffer));

		closeBuffer(buffer);

		innerOutput.accept(buffer.toString());
	}

	// ========================================================================
	//
	//
	//
	// ========================================================================

	private String cmdLine(long min, long max, long maxRam, int threadStack, String configLocation,
			boolean usePreTouch) {
		return 
		   new StringBuilder(1024)
				.append(" -Xms").append(min).append('m')
				.append(" -Xmx").append(max).append('m')
				.append(" -XX:MaxRAM=").append(maxRam).append('m')
				.append(" -Xss").append(threadStack).append('k')
				.append(" -noverify")
				.append(" -XX:TieredStopAtLevel=1")
				.append(" -Dspring.jmx.enabled=false")
				.append(" -Dspring.config.location=").append(configLocation)
				.append((usePreTouch ? " -XX:+AlwaysPreTouch" : ""))
				.append((isWindows()
						? " -Djava.security.egd=file:/dev/urandom" : " -Djava.security.egd=file:/dev/./urandom"))
		  .toString();
	}

	private void showHeapUsage() {
		MemoryUsage heap = getMemoryMXBean().getHeapMemoryUsage();
		long initial = heap.getInit() > 0 ? heap.getInit() / FACTOR : 0;
		long used = heap.getUsed() > 0 ? heap.getUsed() / FACTOR : 0;
		long committed = heap.getCommitted() > 0 ? heap.getCommitted() / FACTOR : 0;
		long max = heap.getMax() > 0 ? heap.getMax() / FACTOR : 0;
		innerOutput.accept(String.format("Heap: Init: %sMB, Used: %sMB, Committed: %sMB, Max: %sMB ", initial, used,
				committed, max));
	}

	
	private void memoryManagement() {
		MemoryUsage heap = getMemoryMXBean().getHeapMemoryUsage();
		long initial = heap.getInit() >0 ? heap.getInit() / FACTOR : 0;
		long innerMax = heap.getMax() >0 ? heap.getMax() / FACTOR : 0;

		if (initial < 200) {
			innerOutput.accept("better to set initial memory to at least 200m");
		}
		this.min = initial < 256 ? 200 : initial;
		if (innerMax > 512) {
			innerOutput.accept("better to review why you're consuming much memory");
		}
		this.max = innerMax < 350 ? 350 : (((768 - innerMax) <= 50) ? 768 : innerMax);
		this.maxRam = this.max >= 768 ? 768 : this.max;
	}

	private final void header(String id) {
		innerOutput.accept("");
		innerOutput.accept("######################################################################");
		innerOutput.accept(String.format("#####  PROFILE FOR ID: %s ", id));
		innerOutput.accept("######################################################################");
		innerOutput.accept("");
	}

	private final void footer() {
		innerOutput.accept("");
		innerOutput.accept("######################################################################");
		innerOutput.accept("");
	}

	private final void section(String msg) {
		innerOutput.accept("");
		innerOutput.accept("######################################################################");
		innerOutput.accept(String.format("#####  %s ", msg));
		innerOutput.accept("######################################################################");
		innerOutput.accept("");
	}

	
	private final StringBuilder openBuffer(){
       return  new StringBuilder(4000)
		    .append(LINE_SEPARTOR)
		    .append("@Import({")
		    .append(LINE_SEPARTOR);
	}
	
	
	private final void closeBuffer(StringBuilder buffer){
		if (buffer.length() > 1) {
			buffer.setLength(buffer.length() - (LINE_SEPARTOR.length() + 1));
		}
		buffer.append("})");
	}
	
	
	// ===============================================================
	//
	//
	//
	// ===============================================================

	private static final Consumer<String> appendCsv(StringBuilder sb) {
		Objects.requireNonNull(sb,"Buffer must be supplied");
		return classsName -> {
			if (isPublicClass(classsName)) {
				sb
				 .append(classsName)
				 .append(".class")
				 .append(',')
				 .append(LINE_SEPARTOR);
			}
		};
	}

	private static final boolean isPublicClass(String fullQualifiedClassName) {
		Objects.requireNonNull(fullQualifiedClassName, "Null Class NAme Passed!...");
		try {
			return (Class.forName(fullQualifiedClassName).getModifiers() == java.lang.reflect.Modifier.PUBLIC);
		} catch (ClassNotFoundException e) {
			logger.warn("Problem finding class: {} --> {} returning false!...",fullQualifiedClassName,
					e.toString());
			return false;
		}
	}

	private static final String createApplicationProperties() {
		return LINE_SEPARTOR + LINE_SEPARTOR + "#DISABLE BANNER" + LINE_SEPARTOR + "spring.main.banner-mode=off"
				+ LINE_SEPARTOR + "#DISABLE STARTUP INFO" + LINE_SEPARTOR + "spring.main.logStartupInfo=false"
				+ LINE_SEPARTOR + "#DISABLE JMX (if not already done in command line)" + LINE_SEPARTOR
				+ "spring.jmx.enabled=false" + LINE_SEPARTOR + "#DISABLE ERROR PAGE" + LINE_SEPARTOR
				+ "server.error.whitelabel.enabled=false" + LINE_SEPARTOR + "#DISABLE JSP REGISTRATION" + LINE_SEPARTOR
				+ "server.jsp-servlet.registered=false" + LINE_SEPARTOR + "#DISABLE TEMPLATING TECHNOLOGIES"
				+ LINE_SEPARTOR + "spring.freemarker.enabled=false" + LINE_SEPARTOR
				+ "spring.groovy.template.enabled=false" + LINE_SEPARTOR + "#DISABLE UPLOAD SUPPORT]" + LINE_SEPARTOR
				+ "spring.http.multipart.enabled=false" + LINE_SEPARTOR + "#DISABLE SITE PREFERENCE FOR MOBILE"
				+ LINE_SEPARTOR + "spring.mobile.sitepreference.enabled=false" + LINE_SEPARTOR
				+ "#DISABLE SESSION TABLE AT STARTUP]" + LINE_SEPARTOR + "spring.session.jdbc.initializer.enabled=false"
				+ LINE_SEPARTOR + "#DISABLE TEMPLATE CACHING]" + LINE_SEPARTOR + "spring.thymeleaf.cache=false"
				+ LINE_SEPARTOR + "#" + LINE_SEPARTOR + "# LOGGING LEVEL" + LINE_SEPARTOR + "#" + LINE_SEPARTOR
				+ "#  show nothing [logging.level.root=WARN]" + LINE_SEPARTOR + "#" + LINE_SEPARTOR
				+ "logging.level.org.springframework.boot=WARN" + LINE_SEPARTOR
				+ "logging.level.org.springframework=WARN" + LINE_SEPARTOR + "logging.level.org.apache.tomcat=WARN"
				+ LINE_SEPARTOR + "logging.level.org.apache.catalina=WARN" + LINE_SEPARTOR
				+ "logging.level.org.eclipse.jetty=WARN" + LINE_SEPARTOR
				+ "logging.level.org.hibernate.tool.hbm2ddl=WARN" + LINE_SEPARTOR
				+ "logging.level.org.hibernate.SQL=WARN" + LINE_SEPARTOR + "#" + LINE_SEPARTOR + "# TOMCAT TUNING"
				+ LINE_SEPARTOR + "#" + LINE_SEPARTOR + "#" + LINE_SEPARTOR + "server.tomcat.min-spare-threads=15"
				+ LINE_SEPARTOR + "server.tomcat.max-threads=45" + LINE_SEPARTOR + "server.tomcat.accept-count=120"
				+ LINE_SEPARTOR + "server.tomcat.max-connections=6000";
	}

	private static final String registerLazyProcTemplate() {
		return LINE_SEPARTOR + LINE_SEPARTOR + "package {0};" + LINE_SEPARTOR + "import java.util.Locale;"
				+ LINE_SEPARTOR + "import org.slf4j.Logger;" + LINE_SEPARTOR + "import org.slf4j.LoggerFactory;"
				+ LINE_SEPARTOR + "import org.springframework.beans.BeansException;" + LINE_SEPARTOR
				+ "import org.springframework.beans.factory.config.BeanFactoryPostProcessor;" + LINE_SEPARTOR
				+ "import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;" + LINE_SEPARTOR
				+ "import org.springframework.context.annotation.Configuration;" + LINE_SEPARTOR + LINE_SEPARTOR
				+ "@Configuration" + LINE_SEPARTOR
				+ "public class LazyInitBeanFactoryPostProcessor implements BeanFactoryPostProcessor {" + LINE_SEPARTOR
				+ "    private static Logger logger = LoggerFactory.getLogger(LazyInitBeanFactoryPostProcessor.class);"
				+ LINE_SEPARTOR + LINE_SEPARTOR + "	@Override" + LINE_SEPARTOR
				+ "	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {"
				+ LINE_SEPARTOR + "		logger.debug(\"start lazy post processor...\");" + LINE_SEPARTOR
				+ "		for (String beanName : beanFactory.getBeanDefinitionNames()) {" + LINE_SEPARTOR
				+ "			if (filteringWith(beanName)) {" + LINE_SEPARTOR
				+ "				logger.debug(\"skipping bean: {}\", beanName);" + LINE_SEPARTOR
				+ "				continue;" + LINE_SEPARTOR + "			}" + LINE_SEPARTOR
				+ "			beanFactory.getBeanDefinition(beanName).setLazyInit(true);" + LINE_SEPARTOR + "		}"
				+ LINE_SEPARTOR + "		logger.debug(\"stop lazy post processor...\");" + LINE_SEPARTOR + "	}"
				+ LINE_SEPARTOR + LINE_SEPARTOR + "	//" + LINE_SEPARTOR
				+ "	// TODO Change this method, based on your need" + LINE_SEPARTOR
				+ "	//      Generally this work fine with spring-fox (swagger2)" + LINE_SEPARTOR + "	//"
				+ LINE_SEPARTOR + "	protected boolean filteringWith(String beanName) {" + LINE_SEPARTOR
				+ "		return beanName.toLowerCase(Locale.ENGLISH).contains(\"mvc\");" + LINE_SEPARTOR + "	}"
				+ LINE_SEPARTOR + LINE_SEPARTOR + "}// END";
	}

	private static final boolean isWindows() {
		return OS.toLowerCase(ENGLISH).contains("win");
	}

	private static Predicate<Map.Entry<String, ConditionAndOutcomes>> fullMatchNoInner() {
		return e -> e.getKey().indexOf('#') < 0 && 
				    e.getKey().indexOf('$') < 0 && 
				    e.getValue().isFullMatch();
	}

}// END