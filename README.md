# osb-12c-maven 
OSB util projects to work with maven (current version -> 12.2.1.4)

- **osb-compile-project:** Compile plugin for OSB projects. Allows compile/deploy of full service or subset of resources. 
- **osb-parent-pom:** Parent pom of the OSB projects (include the compile plugin)
- **osb-project-template:** Maven archetype for OSB projects.

## Requirements

It's necessary to have **JDK** and **maven** installed. 

## Configure

1. Sync of the oracle maven plugin on the maven repository. https://docs.oracle.com/middleware/12213/wls/WLPRG/maven.htm#WLPRG585

2. In the home directory compile and install the dependencies in the repository
```
mvn clean install
```

## Use the archetype

### Create Project

The maven archetype create a new OSB app and OSB project. 

In the next command change the **artifactId**, the **groupId** and the **version** accordingly.

```
mvn archetype:generate -DarchetypeGroupId=com.oracle.osb -DarchetypeArtifactId=osb-project-template -DarchetypeVersion=1.0 -DgroupId=com.test.service -DartifactId=super-service -Dversion=1.0 -DinteractiveMode=false
```

### Compile Project

Lauch next command inside the OSB project to create a package (inside the .data/maven dir)

- Compile the full service
```
mvn -Doracle.home=$ORACLE_HOME -P deploy-osb-service clean package
```

- Compile the service only with the resources included in the deploy-file.xml
```
mvn -Doracle.home=$ORACLE_HOME -Ddeploy.file=deploy-file.xml -P deploy-osb-resources package
```

### Deploy Project

Lauch next command inside the OSB project to deploy the project into the OSB server. 

Configure the parameters:
- server.url
- server.username
- server.password
- deployment.customization.file (optional)

Deploy full service
```
mvn -Doracle.home=$ORACLE_HOME -Dserver.url=http://localhost:7001 -Dserver.username=weblogic -Dserver.password=welcome1 -P deploy-osb-service pre-integration-test
```

Deploy only the resources included in the deploy-file.xml
```
mvn -Doracle.home=$ORACLE_HOME -Dserver.url=http://localhost:7001 -Dserver.username=weblogic -Dserver.password=welcome1 -Ddeploy.file=deploy-file.xml -P deploy-osb-resources pre-integration-test
```

Deploy only the resources included in the deploy-file.xml with a customization file
```
mvn -Doracle.home=$ORACLE_HOME -Dserver.url=http://localhost:7001 -Dserver.username=weblogic -Dserver.password=welcome1 -Ddeployment.customization.file=custom_file.xml -P deploy-osb-service pre-integration-test
```
