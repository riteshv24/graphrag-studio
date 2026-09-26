package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Character-offset preserving chunker with overlapping windows and boundary preference. */
public final class Chunker {
    public record Chunk(int ordinal, int start, int end, String text) {
        public Map<String, Object> json() {
            return Json.map("ordinal", ordinal, "start", start, "end", end, "text", text);
        }
    }
    public List<Chunk> split(String text, int target, int overlap) {
        Checks.range(target, "chunkSize", 200, 2400);
        Checks.range(overlap, "overlap", 0, target/2);
        List<Chunk> chunks=new ArrayList<>();
        int cursor=0;
        while(cursor<text.length()) {
            int end=Math.min(text.length(), cursor+target);
            if(end<text.length()) {
                int floor=cursor+target*2/3, boundary=-1;
                for(int i=end-1; i>=floor; i--)if(text.charAt(i)=='\n') {
                    boundary=i+1;
                    break;
                }
                if(boundary<0)for(int i=end-1; i>=floor; i--)if(Character.isWhitespace(text.charAt(i))) {
                    boundary=i;
                    break;
                }
                if(boundary>cursor)end=boundary;
                if(end<text.length()&&end>cursor&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))end--;
            }
            int start=cursor, trimmedEnd=end;
            while(start<trimmedEnd&&Character.isWhitespace(text.charAt(start)))start++;
            while(trimmedEnd>start&&Character.isWhitespace(text.charAt(trimmedEnd-1)))trimmedEnd--;
            if(trimmedEnd>start)chunks.add(new Chunk(chunks.size(), start, trimmedEnd, text.substring(start, trimmedEnd)));
            if(chunks.size()>180)throw Problem.invalid("Document produces more than 180 chunks; use larger chunks or a smaller document");
            if(end>=text.length())break;
            int next=Math.max(cursor+1, end-overlap);
            if(next<text.length()&&next>0&&Character.isLowSurrogate(text.charAt(next))&&Character.isHighSurrogate(text.charAt(next-1)))next++;
            cursor=next;
        }
        return chunks;
    }
}
