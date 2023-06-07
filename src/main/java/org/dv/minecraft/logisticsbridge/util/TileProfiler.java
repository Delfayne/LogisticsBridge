package org.dv.minecraft.logisticsbridge.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class TileProfiler {
    public final String title;
    private final List<String> sectionList = Lists.newArrayList();
    /**
     * List of timestamps (System.nanoTime)
     */
    private final List<Long> timestampList = Lists.newArrayList();
    /**
     * Profiling map
     */
    private final Map<String, Long> profilingMap = Maps.newHashMap();
    private final Map<String, Long> avgProfilingMap = Maps.newHashMap();
    public EntityPlayer resultPlayer;
    /**
     * Flag profiling enabled
     */
    public boolean profilingEnabled;
    public String lastSection;
    /**
     * Current profiling section
     */
    private String profilingSection = "";
    private int cycles;

    public TileProfiler(String title) {
        this.title = title;
    }

    private static void appendMap(StringBuilder b, String tab, Map<String, Object> m) {
        for (Entry<String, Object> e : m.entrySet()) {
            if (!e.getKey().equals("_v")) {
                Map<String, Object> map = (Map<String, Object>) e.getValue();
                b.append(tab);
                b.append(e.getKey());
                b.append(": ");
                b.append(map.get("_v"));
                b.append(" ms\n");
                appendMap(b, tab + "| ", map);
            }
        }
    }

    public void startProfiling() {
        if (resultPlayer != null) {
            profilingEnabled = true;
            startSection("root");
        }
    }

    public void finishProfiling() {
        cycles--;
        if (resultPlayer != null) {
            endSection();
            profilingEnabled = false;
            profilingMap.forEach((n, t) -> avgProfilingMap.merge(n, t, (a, b) -> Long.valueOf((a * 3 + b) / 4)));
            this.profilingMap.clear();
            this.profilingSection = "";
            this.sectionList.clear();
        }

        if (cycles < 1) {
            sendResults();
        }

        lastSection = null;
    }

    public void startSection(String name) {
        if (this.profilingEnabled) {
            if (!this.profilingSection.isEmpty()) {
                this.profilingSection = this.profilingSection + ".";
            }

            this.profilingSection = this.profilingSection + name;
            this.sectionList.add(this.profilingSection);
            this.timestampList.add(Long.valueOf(System.nanoTime()));
        } else {
            this.lastSection = name;
        }
    }

    public void endSection() {
        if (this.profilingEnabled) {
            long i = System.nanoTime();
            long j = this.timestampList.remove(this.timestampList.size() - 1).longValue();
            this.sectionList.remove(this.sectionList.size() - 1);
            long k = i - j;

            if (this.profilingMap.containsKey(this.profilingSection)) {
                this.profilingMap.put(this.profilingSection, Long.valueOf(this.profilingMap.get(this.profilingSection).longValue() + k));
            } else {
                this.profilingMap.put(this.profilingSection, Long.valueOf(k));
            }

            this.profilingSection = this.sectionList.isEmpty() ? "" : this.sectionList.get(this.sectionList.size() - 1);
        }
    }

    public void endStartSection(String name) {
        this.endSection();
        this.startSection(name);
    }

    public void sendResults() {
        profilingEnabled = false;
        if (resultPlayer != null) {
            StringBuilder r = new StringBuilder();
            r.append("�3===�r �eTileProfiler report (" + title + ")�r �3===�r\n");
            Map<String, Object> map = new HashMap<>();
            avgProfilingMap.forEach((n, t) -> {
                Map<String, Object> m = map;
                String[] sp = n.split("\\.");
                for (int i = 0; i < sp.length; i++) {
                    m = (Map<String, Object>) m.computeIfAbsent(sp[i], k -> new HashMap<>());
                }
                m.put("_v", (t / 1000) / 1000f);
				/*r.append(n);
				r.append(": ");
				r.append((t / 1000) / 1000f);
				r.append(" ms\n");*/
            });
            appendMap(r, "", map);
            resultPlayer.sendMessage(new TextComponentString(r.toString()));
            resultPlayer = null;
        }
        this.profilingMap.clear();
        this.profilingSection = "";
        this.sectionList.clear();
        avgProfilingMap.clear();
    }

    public void setResultPlayer(EntityPlayer resultPlayer, int cycles) {
        this.resultPlayer = resultPlayer;
        this.cycles = cycles;
    }
}
