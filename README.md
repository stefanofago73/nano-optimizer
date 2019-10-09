## Nano Optimizer ##

***WARNING***</br>
Work in Progress

***WARNING***</br>
This is a Sunday project ... you don't expect anything of high </br>
quality but ... I hope you need it, even as a starting point. </br>


### The Idea ###


The idea is to collect, as code, the main practices on optimizing Spring Boot, 1.5.17 </br>
and above but not 2.x performance, with Tomcat as an embedded server.</br>

Once put in the main of the Spring Boot Application, the utility class,</br>
appropriately configured, allows to produce a report indicating the JVM command-line,</br>
the @Import annotation to be used to accelerate the start-up(under the @Configuration of the App),  </br>
it is produced a Lazyness configurator for the startup of the services( you can customize it).</br>
A summary of the application properties is provided which could be turned off to speed up</br>
the start and / or decrease memory consumption.</br>

more information can be found at the following links:
[allocation](https://github.com/dsyer/spring-boot-allocations)</br>
[memory](https://github.com/dsyer/spring-boot-memory-blog/)</br>
[autoconfig](https://geowarin.com/understanding-spring-boot/)



### Configuration Examples ###

When you are ready, you will modify the main of your Service as follow:

```java
@SpringBootApplication
public class MyApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(MyApplication.class);
		
		NanoOptimizer
		  .optimizerFor("sample1", context)
		  .generateJavaCmdLine(false)
		  .generateLazyProcessor("it.fago.demo")
		  .useDefaultForMemory()
		  .build();
	}

}
```
...while configuration examples are the following:

```Java
     NanoOptimizer
		  .optimizerFor("sample1", context)      // id is the identifier of the report while context is the Spring Context
		  .generateJavaCmdLine(false)            // false imply -XX:+AlwaysPreTouch not added to command-line
		  .generateLazyProcessor("it.fago.demo") // here with specify the base package for the LazyPostProcessor
		  .useDefaultForMemory()                 // on the command-line, are used memory parameter following some best-practices
		  .build();                              // the report is done on console or actual logger for the package
```


```Java
    NanoOptimizer
		  .optimizerFor("sample2", context)
		  .generateJavaCmdLine(true, "file://") // -XX:+AlwaysPreTouch  is used on command-line and you can specify where application.properties is...
		  .generateLazyProcessor("it.fago.demo2")
		.build();
```


```Java
    NanoOptimizer
		  .optimizerFor("sample3", context)
		  .useDefualtForMemory()
		  .generateJavaCmdLine(true)
		  .generateLazyProcessor("it.fago.demo")
		.build("/var/share/reports","_DEMO_"+System.nanoTime()); // the report is stored in a file with name==id+"_REPORT_"+suffix
```
