/*
 * SonarSource :: Web :: Sonar Plugin
 * Copyright (c) 2010-2017 SonarSource SA and Matthijs Galesloot
 * sonarqube@googlegroups.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sonar.plugins.web.checks.header;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.web.checks.AbstractPageCheck;
import org.sonar.plugins.web.node.Node;
import org.sonar.plugins.web.visitor.CharsetAwareVisitor;
import org.sonar.squidbridge.api.AnalysisException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Rule(key = "HeaderCheck")
public class HeaderCheck extends AbstractPageCheck implements CharsetAwareVisitor {

  private static final String DEFAULT_HEADER_FORMAT = "";
  private static final String MESSAGE = "Add or update the header of this file.";

  @RuleProperty(
    key = "headerFormat",
    description = "Expected copyright and license header (plain text)",
    defaultValue = DEFAULT_HEADER_FORMAT,
    type = "TEXT")
  public String headerFormat = DEFAULT_HEADER_FORMAT;

  @RuleProperty(
    key = "isRegularExpression",
    description = "Whether the headerFormat is a regular expression",
    defaultValue = "false")
  public boolean isRegularExpression = false;

  private Charset charset;
  private String[] expectedLines;
  private Pattern searchPattern = null;

  @Override
  public void setCharset(Charset charset) {
    this.charset = charset;
  }

  @Override
  public void init() {
    if (isRegularExpression) {
      try {
        searchPattern = Pattern.compile(headerFormat, Pattern.DOTALL);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("[" + getClass().getSimpleName() + "] Unable to compile the regular expression: " + headerFormat, e);
      }
    } else {
      expectedLines = headerFormat.split("(?:\r)?\n|\r");
    }
  }

  @Override
  public void startDocument(List<Node> nodes) {
    if (isRegularExpression) {
      String fileContent;
      try {
        fileContent = Files.toString(getWebSourceCode().inputFile().file(), charset);
      } catch (IOException e) {
        throw new AnalysisException(e);
      }
      checkRegularExpression(fileContent);
    } else {
      LineProcessor<Boolean> processor = new HeaderLinesProcessor(expectedLines);
      try {
        Files.readLines(getWebSourceCode().inputFile().file(), charset, processor);
      } catch (IOException e) {
        throw new AnalysisException(e);
      }
      if (!processor.getResult()) {
        createViolation(0, MESSAGE);
      }
    }
  }

  private void checkRegularExpression(String fileContent) {
    Matcher matcher = searchPattern.matcher(fileContent);
    if (!matcher.find() || matcher.start() != 0) {
      createViolation(0, MESSAGE);
    }
  }

  private static class HeaderLinesProcessor implements LineProcessor<Boolean> {

    private boolean result = false;
    private int lineNumber = 0;
    private String[] expectedLines;

    HeaderLinesProcessor(String[] expectedLines) {
      this.expectedLines = expectedLines;
    }

    @Override
    public boolean processLine(String line) throws IOException {
      lineNumber++;
      if (lineNumber == 1) {
        result = true;
      }
      if (lineNumber > expectedLines.length) {
        // we are done checking, stop processor
      } else if (line.equals(expectedLines[lineNumber - 1])) {
        return true;
      } else {
        result = false;
      }
      return false;
    }

    @Override
    public Boolean getResult() {
      return result && lineNumber >= expectedLines.length;
    }

  }

}
