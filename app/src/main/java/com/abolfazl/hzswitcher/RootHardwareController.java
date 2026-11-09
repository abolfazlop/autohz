package com.abolfazl.hzswitcher;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Device-agnostic, read-before-write hardware access layer.
 * No SoC names, policy numbers, frequency ranges or sysfs paths are assumed.
 * All writes are bounded by values discovered from the running kernel.
 */
public final class RootHardwareController {
    private RootHardwareController() {}

    public static final class Result {
        public final int exitCode;
        public final String output;
        public Result(int code, String text) { exitCode=code; output=text==null?"":text.trim(); }
        public boolean ok() { return exitCode==0; }
    }

    public static final class CpuPolicy {
        public final String path, relatedCpus, governor, governors;
        public final long minKhz, maxKhz, hardwareMinKhz, hardwareMaxKhz;
        CpuPolicy(String p,String cpus,String gov,String available,long min,long max,long hwMin,long hwMax) {
            path=p; relatedCpus=cpus; governor=gov; governors=available;
            minKhz=min; maxKhz=max; hardwareMinKhz=hwMin; hardwareMaxKhz=hwMax;
        }
    }

    public static final class GpuDevice {
        public final String path, name, governor, governors;
        public final long minHz,maxHz,hardwareMinHz,hardwareMaxHz;
        GpuDevice(String p,String n,String g,String gs,long min,long max,long hmin,long hmax) {
            path=p;name=n;governor=g;governors=gs;minHz=min;maxHz=max;hardwareMinHz=hmin;hardwareMaxHz=hmax;
        }
    }

    /** Executes a bounded root command through KernelSU/Magisk/APatch's su interface. */
    public static Result run(String script, long timeoutMs) {
        Process process=null;
        try {
            process=new ProcessBuilder("su","-c",script).redirectErrorStream(true).start();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            // Read synchronously with a bounded wait; sysfs probes are short.
            try(InputStream in=process.getInputStream()) {
                byte[] b=new byte[4096]; int n;
                while((n=in.read(b))!=-1) out.write(b,0,n);
            }
            boolean ended=process.waitFor(timeoutMs,TimeUnit.MILLISECONDS);
            if(!ended){process.destroyForcibly();return new Result(124,"root command timed out");}
            return new Result(process.exitValue(),out.toString("UTF-8"));
        } catch(Exception e) {
            if(process!=null)process.destroyForcibly();
            return new Result(127,e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
    // Kept private so no shell process or stream escapes the controller.
    private static InputStream processInput(Process p) throws IOException { return p.getInputStream(); }
    private static final ThreadLocal<Process> processRef=new ThreadLocal<>();

    public static boolean hasRoot() {
        Result r=run("id -u",4000);
        return r.ok() && r.output.trim().equals("0");
    }

    /** Discover every cpufreq policy and use kernel-reported bounds, not guessed values. */
    public static List<CpuPolicy> discoverCpuPolicies() {
        String helper="/data/adb/modules/pulsext_touch_engine/pulsext-hwprobe";
        String script="if [ -x '"+helper+"' ]; then '"+helper+"' cpu; else "+
          "for d in /sys/devices/system/cpu/cpufreq/policy*; do "+
          "[ -d \"$d\" ] || continue; "+
          "readv(){ [ -r \"$d/$1\" ] && cat \"$d/$1\" 2>/dev/null || echo ''; }; "+
          "echo POLICY:$d; echo CPUS:$(readv related_cpus); echo GOV:$(readv scaling_governor); "+
          "echo GOVS:$(readv scaling_available_governors); "+
          "echo HMIN:$(readv cpuinfo_min_freq); echo HMAX:$(readv cpuinfo_max_freq); "+
          "echo MIN:$(readv scaling_min_freq); echo MAX:$(readv scaling_max_freq); done; fi";
        Result r=run(script,7000); ArrayList<CpuPolicy> out=new ArrayList<>();
        if(!r.ok())return out;
        String[] lines=r.output.split("\\r?\\n"); Map<String,String> m=new HashMap<>();
        for(String line:lines){
            int k=line.indexOf(':'); if(k<0)continue;
            String key=line.substring(0,k),v=line.substring(k+1).trim();
            if(key.equals("POLICY")){if(!m.isEmpty())addPolicy(out,m);m=new HashMap<>();}
            m.put(key,v);
        }
        if(!m.isEmpty())addPolicy(out,m);
        return out;
    }
    private static void addPolicy(List<CpuPolicy> out,Map<String,String> m){
        long hmin=num(m.get("HMIN")),hmax=num(m.get("HMAX"));
        long min=num(m.get("MIN")),max=num(m.get("MAX"));
        if(m.get("POLICY")==null||hmin<=0||hmax<hmin)return;
        out.add(new CpuPolicy(m.get("POLICY"),m.get("CPUS"),m.get("GOV"),m.get("GOVS"),
          Math.max(hmin,Math.min(min,hmax)),Math.max(hmin,Math.min(max,hmax)),hmin,hmax));
    }

    /** Validate bounds against the live policy immediately before writing. */
    public static Result setCpuLimits(String policyPath,long minKhz,long maxKhz) {
        if(policyPath==null||!policyPath.matches("/sys/devices/system/cpu/cpufreq/policy[0-9]+"))return new Result(2,"invalid CPU policy path");
        List<CpuPolicy> policies=discoverCpuPolicies(); CpuPolicy p=null;
        for(CpuPolicy x:policies)if(x.path.equals(policyPath)){p=x;break;}
        if(p==null)return new Result(3,"CPU policy not detected");
        if(minKhz<p.hardwareMinKhz||maxKhz>p.hardwareMaxKhz||minKhz>maxKhz)return new Result(4,"requested CPU limits outside detected hardware range");
        String base=p.path;
        // Avoid transient min>max/max<min conflicts while moving the policy window.
        String command="curmin=$(cat '"+base+"/scaling_min_freq') && curmax=$(cat '"+base+"/scaling_max_freq') && "+
          "if [ "+maxKhz+" -lt \"$curmin\" ]; then "+
          "echo "+minKhz+" > '"+base+"/scaling_min_freq' && echo "+maxKhz+" > '"+base+"/scaling_max_freq'; "+
          "else echo "+maxKhz+" > '"+base+"/scaling_max_freq' && echo "+minKhz+" > '"+base+"/scaling_min_freq'; fi && "+
          "[ \"$(cat '"+base+"/scaling_min_freq')\" = '"+minKhz+"' ] && "+
          "[ \"$(cat '"+base+"/scaling_max_freq')\" = '"+maxKhz+"' ]";
        return run(command,5000);
    }

    public static Result setCpuGovernor(String policyPath,String governor) {
        if(policyPath==null||!policyPath.matches("/sys/devices/system/cpu/cpufreq/policy[0-9]+")||governor==null||!governor.matches("[A-Za-z0-9_]+"))return new Result(2,"invalid policy or governor");
        for(CpuPolicy p:discoverCpuPolicies())if(p.path.equals(policyPath)){
            if(!Arrays.asList(p.governors.split("\\s+")).contains(governor))return new Result(4,"governor not supported by this policy");
            return run("echo '"+governor+"' > '"+p.path+"/scaling_governor' && [ \"$(cat '"+p.path+"/scaling_governor')\" = '"+governor+"' ]",5000);
        }
        return new Result(3,"CPU policy not detected");
    }

    /** Discover generic devfreq GPU candidates. No vendor path is assumed. */
    public static List<GpuDevice> discoverGpuDevices() {
        String script="for d in /sys/class/devfreq/* /sys/devices/platform/*/devfreq/*; do "+
          "[ -d \"$d\" ] || continue; "+
          "[ -r \"$d/available_frequencies\" ] || [ -r \"$d/cur_freq\" ] || continue; "+
          "case \"$(basename \"$d\" | tr '[:upper:]' '[:lower:]')\" in *gpu*|*mali*|*3d*) ;; *) continue;; esac; "+
          "echo GPU:$d; for f in name governor available_governors min_freq max_freq cur_freq available_frequencies; do "+
          "[ -r \"$d/$f\" ] && echo \"$f:$(cat \"$d/$f\" 2>/dev/null)\"; done; done";
        Result r=run(script,7000);ArrayList<GpuDevice> out=new ArrayList<>();if(!r.ok())return out;
        String path=null,name="",gov="",govs="",available="";long min=0,max=0,cur=0;
        for(String line:r.output.split("\\r?\\n")){
            if(line.startsWith("GPU:")){
                if(path!=null)addGpu(out,path,name,gov,govs,min,max,cur,available);
                path=line.substring(4).trim();name="";gov="";govs="";available="";min=max=cur=0;continue;
            }
            int k=line.indexOf(':');if(k<0)continue;String key=line.substring(0,k),v=line.substring(k+1).trim();
            if(key.equals("name"))name=v;else if(key.equals("governor"))gov=v;else if(key.equals("available_governors"))govs=v;
            else if(key.equals("min_freq"))min=num(v);else if(key.equals("max_freq"))max=num(v);else if(key.equals("cur_freq"))cur=num(v);
            else if(key.equals("available_frequencies"))available=v;
        }
        if(path!=null)addGpu(out,path,name,gov,govs,min,max,cur,available);
        return out;
    }
    private static void addGpu(List<GpuDevice> out,String p,String n,String g,String gs,long min,long max,long cur,String available){
        // Prefer the driver's advertised frequency table as the hardware envelope.
        long tableMin=Long.MAX_VALUE,tableMax=0;
        for(String token:available.split("[\\s,]+")){
            long value=num(token);if(value>0){tableMin=Math.min(tableMin,value);tableMax=Math.max(tableMax,value);}
        }
        long lo=tableMax>0?tableMin:(min>0?min:cur);
        long hi=tableMax>0?tableMax:(max>0?max:cur);
        if(lo>0&&hi>=lo)out.add(new GpuDevice(p,n,g,gs,min,max,lo,hi));
    }
    public static Result setGpuLimits(GpuDevice gpu,long min,long max) {
        if(gpu==null||min<gpu.hardwareMinHz||max>gpu.hardwareMaxHz||min>max)return new Result(4,"GPU limits outside detected range");
        String p=gpu.path;
        if(!p.matches("/sys/(class/devfreq|devices/platform)/[A-Za-z0-9_./-]+"))return new Result(2,"invalid GPU path");
        String minFile=new File(p,"min_freq").exists()?p+"/min_freq":p+"/userspace/set_freq";
        String maxFile=p+"/max_freq";
        if(!new File(maxFile).exists())return new Result(5,"GPU max_freq control unavailable");
        if(!new File(minFile).exists())return new Result(5,"GPU min_freq control unavailable");
        // As with CPUFreq, lower the maximum before raising the minimum.
        // Verify both values; a successful shell write alone is not proof that
        // the vendor devfreq driver accepted the requested limits.
        return run("echo "+max+" > '"+maxFile+"' && echo "+min+" > '"+minFile+"' && "+
          "[ \"$(cat '"+minFile+"')\" = '"+min+"\" ] && "+
          "[ \"$(cat '"+maxFile+"')\" = '"+max+"\" ]",5000);
    }
    public static Result setGpuGovernor(GpuDevice gpu,String governor) {
        if(gpu==null||governor==null||!governor.matches("[A-Za-z0-9_]+"))return new Result(2,"invalid GPU or governor");
        if(!Arrays.asList(gpu.governors.split("\\s+")).contains(governor))return new Result(4,"GPU governor unsupported");
        return run("echo '"+governor+"' > '"+gpu.path+"/governor' && [ \"$(cat '"+gpu.path+"/governor')\" = '"+governor+"' ]",5000);
    }
    private static long num(String s){try{return Long.parseLong(s==null?"":s.trim());}catch(Exception e){return 0;}}
}
