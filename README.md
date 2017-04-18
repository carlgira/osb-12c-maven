# osb-12c-maven
osb-12c-maven

1. Do the sync of the oracle maven plugin. https://docs.oracle.com/middleware/1221/core/MAVEN/config_maven.htm#MAVEN8853

2. Configure in the settings.xml the oracle maven server. (use the settings.xml in the repository as example and put the user and password).

3. Go into the directory "servicebus-plugin" and do a maven install:
	- mvn clean install

4. Go into the directory "osb-parent-pom" and do a maven install:
	- mvn clean install

5. Use the ServiceBusApplication to test the "package" and the "deploy".

	5.1 To package use the next:
	
		- mvn -Doracle.home=/u01/app/oracle/fmw/12.2 package

	5.2 To deploy use the next:
	
		- mvn -Doracle.home=/u01/app/oracle/fmw/12.2 -Dserver.url=http://localhost:7001 -Dserver.username=weblogic -Dserver.password=welcome1 pre-integration-test
		
		-  mvn -Doracle.home=/u01/app/oracle/fmw/12.2 -Dserver.url=http://localhost:7001 -Dserver.username=weblogic -Dserver.password=welcome1 -Ddeployment.customization.file=custom_file.xml pre-integration-test
