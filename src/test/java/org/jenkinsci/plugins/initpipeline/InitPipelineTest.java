package org.jenkinsci.plugins.initpipeline;

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Created by dsm on 5/10/16.
 */




public class InitPipelineTest extends TestCase {

    private InitPipeline initPipeline;
    private String urlPassed;


    @Before
    public void setUp() throws Exception {
        this.initPipeline = new InitPipeline();
        this.urlPassed = "ssh://git@stash.ops.aol.com:2022/pdevops/jenkins-basic-job-template.git";
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testGetDisplayName() throws Exception {
        assertEquals("Git", this.initPipeline.getDisplayName());

    }

    @Test
    public void testGetSearchUrl() throws Exception {
        assertEquals("git", this.initPipeline.getSearchUrl());
    }

    @Test
    public void testGetIconFileName() throws Exception {
        assertNull(this.initPipeline.getIconFileName());
    }

    @Test
    public void testGetUrlName() throws Exception {
        assertEquals("git", this.initPipeline.getUrlName());
    }

    @Test
    public void testToString() throws Exception {
        assertEquals("ssh://git@stash.ops.aol.com:2022/pdevops/jenkins-basic-job-template.git", urlPassed.toString());
    }

    @Test
    public void testDoCreateJob() throws Exception {
        // calls rest of the functions
    }

    @Test
    public void testGetProjectAndRepo() throws Exception {

    }

    @Test
    public void testEditConfigXml() throws Exception {

    }

    @Test
    public void testCallAndCreateJob() throws Exception {

    }

    @Test
    public void testEnableNewJob() throws Exception {

    }

    @Test
    public void testResendNotifyCommit() throws Exception {

    }

    @Test
    public void testOnNotifyCommit1() throws Exception {

    }

    @Test
    public void testOnNotifyCommit2() throws Exception {

    }

    @Test
    public void testOnNotifyCommit3() throws Exception {

    }



}