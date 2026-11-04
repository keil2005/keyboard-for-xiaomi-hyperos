package com.foldkb.deck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼音输入引擎：累积拼音串，匹配候选字/词。
 * 词优先（精确 + 前缀），再单字（精确 + 前缀）。
 * 候选结果供流水屏展示，点选上屏。
 */
public final class PinyinEngine {

    // 单字表：拼音 -> 候选字列表
    private static final Map<String, List<String>> CHAR_MAP = new HashMap<>();
    // 词表：拼音 -> 候选词列表
    private static final Map<String, List<String>> WORD_MAP = new HashMap<>();

    static {
        for (String entry : PinyinDict.CHARS) {
            int i = entry.indexOf(':');
            if (i <= 0) continue;
            String py = entry.substring(0, i);
            String chars = entry.substring(i + 1);
            List<String> list = new ArrayList<>();
            for (int j = 0; j < chars.length(); j++) {
                list.add(String.valueOf(chars.charAt(j)));
            }
            CHAR_MAP.put(py, list);
        }
        for (String entry : PinyinDict.WORDS) {
            int i = entry.indexOf(':');
            if (i <= 0) continue;
            String py = entry.substring(0, i);
            String words = entry.substring(i + 1);
            List<String> list = new ArrayList<>();
            list.add(words);
            WORD_MAP.put(py, list);
        }
    }

    private StringBuilder buffer = new StringBuilder();

    /** 追加一个拼音字母 */
    public void addLetter(char c) {
        buffer.append(c);
    }

    /** 退格删除一个字母 */
    public void backspace() {
        if (buffer.length() > 0) buffer.deleteCharAt(buffer.length() - 1);
    }

    /** 清空拼音串 */
    public void clear() {
        buffer.setLength(0);
    }

    /** 当前拼音串 */
    public String getPinyin() {
        return buffer.toString();
    }

    public boolean isEmpty() {
        return buffer.length() == 0;
    }

    /** 获取候选（词优先，再单字；精确优先，再前缀），最多返回 max 个 */
    public List<String> getCandidates(int max) {
        List<String> out = new ArrayList<>();
        String py = buffer.toString();
        if (py.isEmpty()) return out;

        // 1. 词：精确 + 前缀
        addCandidates(WORD_MAP, py, out, max);
        // 2. 单字：精确 + 前缀
        addCandidates(CHAR_MAP, py, out, max);

        // 去重保序
        List<String> dedup = new ArrayList<>();
        for (String s : out) {
            if (!dedup.contains(s)) dedup.add(s);
            if (dedup.size() >= max) break;
        }
        return dedup;
    }

    private static void addCandidates(Map<String, List<String>> map, String py, List<String> out, int max) {
        // 精确匹配
        List<String> exact = map.get(py);
        if (exact != null) {
            for (String s : exact) {
                out.add(s);
                if (out.size() >= max) return;
            }
        }
        // 前缀匹配（输入一半时也出候选）
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            if (out.size() >= max) return;
            if (e.getKey().startsWith(py) && !e.getKey().equals(py)) {
                for (String s : e.getValue()) {
                    out.add(s);
                    if (out.size() >= max) return;
                }
            }
        }
    }
}
