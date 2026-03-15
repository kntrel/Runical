package com.kntrel.mc.runical.core.internal;

import java.util.function.Function;

public final class PlaceholderRenderer {

    private PlaceholderRenderer() {
    }

    public static String render(String template, Function<String, String> resolver) {
        StringBuilder output = new StringBuilder(template.length());
        int length = template.length();

        for (int index = 0; index < length; index++) {
            char current = template.charAt(index);

            if (current == '{') {
                if (index + 1 < length && template.charAt(index + 1) == '{') {
                    output.append('{');
                    index++;
                    continue;
                }

                int end = template.indexOf('}', index + 1);
                if (end > index) {
                    String token = template.substring(index + 1, end);
                    String replacement = resolver.apply(token);
                    if (replacement != null) {
                        output.append(replacement);
                    } else {
                        output.append('{').append(token).append('}');
                    }
                    index = end;
                    continue;
                }
            } else if (current == '}' && index + 1 < length && template.charAt(index + 1) == '}') {
                output.append('}');
                index++;
                continue;
            }

            output.append(current);
        }

        return output.toString();
    }
}
