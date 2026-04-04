package com.tc.traumchatroom.util;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlEscapeUtil {
    // 匹配 URL 的正则表达式
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 对 HTML 特殊字符进行转义，并将 URL 转换为可点击链接
     */
    public String escapeHtmlAndLinkify(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        // 先转义 HTML 特殊字符
        String escaped = HtmlUtils.htmlEscape(input);

        // 将 URL 转换为 <a> 标签
        Matcher matcher = URL_PATTERN.matcher(escaped);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String url = matcher.group(1);
            // 确保 href 属性也经过转义
            String safeUrl = url.replace("\"", "&quot;");
            matcher.appendReplacement(sb,
                    "<a href=\"" + safeUrl + "\" target=\"_blank\" " +
                            "class=\"text-blue-600 hover:text-blue-800 underline\" rel=\"noopener noreferrer\">" +
                            url +
                            "</a>"
            );
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
