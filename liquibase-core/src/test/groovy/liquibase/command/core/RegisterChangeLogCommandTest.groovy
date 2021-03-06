package liquibase.command.core

import liquibase.Scope
import liquibase.changelog.DatabaseChangeLog
import liquibase.configuration.HubConfiguration
import liquibase.configuration.LiquibaseConfiguration
import liquibase.hub.HubServiceFactory
import liquibase.resource.ResourceAccessor
import liquibase.sdk.resource.MockResourceAccessor
import liquibase.test.JUnitResourceAccessor
import liquibase.util.FileUtil
import liquibase.hub.HubService
import liquibase.hub.core.MockHubService
import spock.lang.Specification

class RegisterChangeLogCommandTest extends Specification {

    private String scopeId
    private File outputFile

    def setup() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("liquibase/test-changelog.xml")
        File changelogFile = new File(url.toURI())
        String contents = FileUtil.getContents(changelogFile)
        outputFile = File.createTempFile("registerChangelog-", ".xml", new File("target/test-classes"))
        outputFile.deleteOnExit()
        FileUtil.write(contents, outputFile)

        JUnitResourceAccessor testResourceAccessor = new JUnitResourceAccessor()
        Map<String, Object> scopeMap = new HashMap<>()
        scopeMap.put(Scope.Attr.resourceAccessor.name(), testResourceAccessor)
        scopeMap.put("liquibase.plugin." + HubService.name, MockHubService)
        scopeId = Scope.enter(scopeMap)
    }

    def "cleanup"() {
        Scope.exit(scopeId)
    }

    def "happyPath"() {
        when:
        def outputStream = new ByteArrayOutputStream()

        def command = new RegisterChangeLogCommand()
        command.setHubProjectId(((MockHubService) Scope.currentScope.getSingleton(HubServiceFactory).getService()).projects.get(0).getId())
        command.setOutputStream(new PrintStream(outputStream))
        command.setChangeLogFile(outputFile.getName())
        command.configure([changeLog: new DatabaseChangeLog("com/example/test.xml")])

        def result = command.run()

        def hubChangeLog = command.getHubChangeLog()

        then:
        result.succeeded
        hubChangeLog.id != null
        hubChangeLog.fileName == "com/example/test.xml"
        hubChangeLog.name == "com/example/test.xml"
    }

    def "changeLogAlreadyRegistered"() {
        when:
        def outputStream = new ByteArrayOutputStream()

        def hubConfiguration = LiquibaseConfiguration.getInstance().getConfiguration(HubConfiguration.class)
        def command = new RegisterChangeLogCommand()
        command.setChangeLogFile("changelog.xml")
        DatabaseChangeLog changeLog = new DatabaseChangeLog(".")
        def uuid = UUID.randomUUID().toString()
        changeLog.setChangeLogId(uuid)
        Map<String, Object> argsMap = new HashMap<>()
        argsMap.put("changeLog", changeLog)
        command.configure(argsMap)
        command.setOutputStream(new PrintStream(outputStream))

        def result = command.run()

        then:
        ! result.succeeded
        result.message.contains("is already registered with changeLogId '" + uuid.toString() + "'")
    }
}
