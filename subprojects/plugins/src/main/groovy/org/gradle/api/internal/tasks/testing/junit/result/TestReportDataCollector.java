/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.tasks.testing.*;

import java.io.File;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * Assembles test results. Keeps a copy of the results in memory to provide them later and spools test output to file.
 *
 * by Szczepan Faber, created at: 11/13/12
 */
public class TestReportDataCollector implements TestListener, TestOutputListener, TestResultsProvider {
    private final Map<String, TestClassResult> results = new HashMap<String, TestClassResult>();
    private final TestResultSerializer resultSerializer;
    private final File resultsDir;
    private final PersistedTestOutput.Writer persistedTestOutputWriter;
    private final PersistedTestOutput.Reader persistedTestOutputReader;

    public TestReportDataCollector(File resultsDir) {
        this(resultsDir, new PersistedTestOutput(resultsDir), new TestResultSerializer());
    }

    TestReportDataCollector(File resultsDir, PersistedTestOutput persistedTestOutput, TestResultSerializer resultSerializer) {
        this(resultsDir, persistedTestOutput.writer(), persistedTestOutput.reader(), resultSerializer);
    }

    public TestReportDataCollector(File resultsDir, PersistedTestOutput.Writer persistedTestOutputWriter, PersistedTestOutput.Reader persistedTestOutputReader, TestResultSerializer resultSerializer) {
        this.resultSerializer = resultSerializer;
        this.resultsDir = resultsDir;
        this.persistedTestOutputWriter = persistedTestOutputWriter;
        this.persistedTestOutputReader = persistedTestOutputReader;
    }

    public void beforeSuite(TestDescriptor suite) {
    }

    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() == null) {
            persistedTestOutputWriter.finishOutputs();
            writeResults();
        }
    }

    private void writeResults() {
        resultSerializer.write(results.values(), resultsDir);
    }

    public void beforeTest(TestDescriptor testDescriptor) {
    }

    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        if (!testDescriptor.isComposite()) {
            String className = testDescriptor.getClassName();
            TestMethodResult methodResult = new TestMethodResult(testDescriptor.getName(), result);
            TestClassResult classResult = results.get(className);
            if (classResult == null) {
                classResult = new TestClassResult(className, result.getStartTime());
                results.put(className, classResult);
            }
            classResult.add(methodResult);
        }
    }

    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        String className = testDescriptor.getClassName();
        if (className == null) {
            //this means that we receive an output before even starting any class (or too late).
            //we don't have a place for such output in any of the reports so skipping.
            return;
        }
        TestDescriptorInternal testDescriptorInternal = (TestDescriptorInternal) testDescriptor;
        TestClassResult classResult = results.get(className);
        if (classResult == null) {
            classResult = new TestClassResult(className, 0);
            results.put(className, classResult);
        }
        persistedTestOutputWriter.onOutput(testDescriptorInternal, outputEvent.getDestination(), outputEvent.getMessage());
    }

    public void visitClasses(Action<? super TestClassResult> visitor) {
        for (TestClassResult classResult : results.values()) {
            visitor.execute(classResult);
        }
    }

    public boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        return persistedTestOutputReader.hasOutput(className, destination);
    }

    public void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
        persistedTestOutputReader.readTo(className, destination, writer);
    }

    public void writeOutputs(String className, String testCaseName, TestOutputEvent.Destination destination, Writer writer) {
        persistedTestOutputReader.readTo(className, testCaseName, destination, writer);
    }
}