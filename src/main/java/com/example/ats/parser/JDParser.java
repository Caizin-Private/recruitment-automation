package com.example.ats.parser;

import com.example.ats.model.JDRequirements;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JDParser {

    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile("(\\d+(\\.\\d+)?)\\s*(\\+)?\\s*(years|yrs)");

    public JDRequirements parse(String jdText){

        Matcher matcher =
                EXPERIENCE_PATTERN.matcher(
                        jdText.toLowerCase());

        double max = 0;

        while(matcher.find()){

            double years =
                    Double.parseDouble(
                            matcher.group(1));

            max = Math.max(max, years);
        }

        return new JDRequirements(max);
    }
}