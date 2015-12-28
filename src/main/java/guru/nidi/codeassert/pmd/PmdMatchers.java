/*
 * Copyright (C) 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.codeassert.pmd;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleViolation;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;

/**
 *
 */
public class PmdMatchers {
    private PmdMatchers() {
    }

    public static Matcher<PmdAnalyzer> hasNoPmdViolations() {
        return new PmdMatcher();
    }

    private static class PmdMatcher extends TypeSafeMatcher<PmdAnalyzer> {
        private List<RuleViolation> violations;

        @Override
        protected boolean matchesSafely(PmdAnalyzer item) {
            violations = item.analyze();
            return violations.isEmpty();
        }

        public void describeTo(Description description) {
            description.appendText("Has no PMD issues");
        }

        @Override
        protected void describeMismatchSafely(PmdAnalyzer item, Description description) {
            for (final RuleViolation violation : violations) {
                description.appendText("\n").appendText(printViolation(violation));
            }
        }

        private String printViolation(RuleViolation violation) {
            final Rule rule = violation.getRule();
            return String.format("%-11s %-45s %s.%s:%d    %s",
                    rule.getPriority(), rule.getName(), violation.getPackageName(), PmdUtils.className(violation), violation.getBeginLine(), violation.getDescription());
        }
    }

}
