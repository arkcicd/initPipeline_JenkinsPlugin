import java.util.*;
/**
 * Entry point to auto-generated tests (generated by maven-hpi-plugin).
 * If this fails to compile, you are probably using Hudson &lt; 1.327. If so, disable
 * this code generation by configuring maven-hpi-plugin to &lt;disabledTestInjection>true&lt;/disabledTestInjection>.
 */
public class InjectedTest extends junit.framework.TestCase {
  public static junit.framework.Test suite() throws Exception {
    Map parameters = new HashMap();
    parameters.put("basedir","/Users/DougMunsinger/Google Drive/arkCICD/arkCICD_GM/InitPipeline_Jenkins_Plugin");
    parameters.put("artifactId","initpipeline");
    parameters.put("outputDirectory","/Users/DougMunsinger/Google Drive/arkCICD/arkCICD_GM/InitPipeline_Jenkins_Plugin/target/classes");
    parameters.put("testOutputDirectory","/Users/DougMunsinger/Google Drive/arkCICD/arkCICD_GM/InitPipeline_Jenkins_Plugin/target/test-classes");
    parameters.put("requirePI","true");
    return new org.jvnet.hudson.test.PluginAutomaticTestBuilder().build(parameters);
  }
}