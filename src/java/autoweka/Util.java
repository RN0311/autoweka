package autoweka;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Pattern;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;

/**
 * Bunches of random stuff that seems to be handy in all sorts of places
 */
public class Util
{
    /**
     * Given a property string (var1=val1:var2=val2:....) convert it to a property object.
     *
     * Note that this honours escaped colons
     */
    static public Properties parsePropertyString(String propStr)
    {
        Properties props = new Properties();
        parsePropertyString(props, propStr);
        return props;
    }

    //Because some people don't like backslashes....
    //private static Pattern msPropertyStringPattern = Pattern.compile("(?<!\\\\):");
    private static Pattern msPropertyStringPattern = Pattern.compile("(?<!\\[\\]|\\[\\@\\]|\\\\|__COLONESCAPE__):");

    /**
     * Given a property string (var1=val1:var2=val2:....) convert it to a property object.
     *
     * Note that this honours escaped colons
     */
    static public void parsePropertyString(Properties props, String propStr)
    {
        //Deal with silly users
        if(propStr == null || propStr.isEmpty())
            return;

        String[] strArray = msPropertyStringPattern.split(propStr);
        for(String str:strArray)
        {
            int equalsIndex = str.indexOf("=");
            if(equalsIndex == -1)
            {
                throw new RuntimeException("Invalid property '" + str + "'");
            }
            String key = str.substring(0,equalsIndex);
            //Still to do with those pesky backslashes
            //String value = str.substring(equalsIndex+1).replace("\\:", ":");
            //String value = str.substring(equalsIndex+1).replace("[@]:", ":");
            String value = str.substring(equalsIndex+1).replace("[@]:", ":").replace("\\:", ":").replace("[]:", ":").replace("__COLONESCAPE__:", ":");
            props.setProperty(key, value);
        }
    }

    /**
     * Converts a Properties object to a property string, escaping ':' as needed
     */
    static public String propertiesToString(Properties props){
        StringBuilder sb = new StringBuilder();

        boolean first = true;
        for(String key: props.stringPropertyNames()){
            if(!first){
                sb.append(":");
            }
            first = false;
            sb.append(key);
            sb.append("=");
            //More backslashes
            //sb.append(props.getProperty(key).replaceAll("\\:", "\\\\:"));
            //sb.append(props.getProperty(key).replaceAll("\\:", "[\\@]:"));
            //sb.append(props.getProperty(key).replaceAll("\\:", "[]:"));
            sb.append(props.getProperty(key).replaceAll("\\:", "__COLONESCAPE__:"));
        }
        return sb.toString();
    }

    /**
     * Looks for command line arguments of the form -prop PROPERTYSTRING and adds them to the given properties object
     */
    static public void parseCommandLineProperties(Properties props, String[] cmdLineArgs)
    {
        parseCommandLineProperties(props, Arrays.asList(cmdLineArgs));
    }
    
    /**
     * Looks for command line arguments of the form -prop PROPERTYSTRING and adds them to the given properties object
     */
    static public void parseCommandLineProperties(Properties props, List<String> cmdLineArgs)
    {
        for(int i = 0; i < cmdLineArgs.size(); i++)
        {
            if(cmdLineArgs.get(i).startsWith("-prop"))
            {
                parsePropertyString(props, cmdLineArgs.get(++i)); 
            }
        }
    }

    /**
     * Makes folders along the specified path
     */
    static public void makePath(String basePath)
    {
        File path = new File(basePath);
        try
        {
            if(!path.exists())
            {
                if(!path.mkdirs())
                    //Did someone sneaky make it?
                    if(!path.exists())
                        throw new RuntimeException("Failed to create directory:" + basePath);
            }
            else if(!path.isDirectory())
            {
                throw new RuntimeException("Something is already has this name, and it's not a dir: " + basePath);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException("Failed to create directory:" + basePath);
        }
    }
    /**
     * Tries to get the current classpath of the running JVM and build a classpath that removes all relative paths
     */
    static public String getAbsoluteClasspath()
    {
        Properties systemProps = System.getProperties();
        String[] paths = systemProps.getProperty("java.class.path").split(systemProps.getProperty("path.separator"));
        
        for(int i = 0; i < paths.length; i++) {
            paths[i] = new java.io.File(paths[i]).getAbsolutePath();
        }
        return joinStrings(systemProps.getProperty("path.separator"), paths);
    }
    
    /**
     * Tries to get the full path to the Java Executable that we're running
     */
    public static String getJavaExecutable()
    {
        return System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    /**
     * Tries to find the given executable on the path
     */
    public static File findExecutableOnPath(String executableName)
    {
        //Check, did they specify something we can resolve?
        File fullyQualifiedExecutable = null;

        fullyQualifiedExecutable = new File(expandPath(executableName));
        //if(1 > 0)
        //    return new File(executableName);
        if(fullyQualifiedExecutable.isFile())
        {
            return fullyQualifiedExecutable;
        }
        //Otherwise, keep looking
        String systemPath = System.getenv("PATH");
        String[] pathDirs = systemPath.split(File.pathSeparator);

        for (String pathDir : pathDirs)
        {
            fullyQualifiedExecutable = new File(pathDir, executableName);
            if (fullyQualifiedExecutable.isFile())
            {
                return fullyQualifiedExecutable;
            }
        }
        //We failed to find it
        return null;
    }

    /**
     * Tries to evaluate the environment variable into something useful.
     *
     * Note, this probably only works on posix systems now...
     */
    public static String expandEnvironmentVariable(String var)
    {
        if(System.getProperty("os.name").toLowerCase().indexOf("win") == 0){
            try
            {
                String[] splitVar = var.split(java.io.File.pathSeparator);
                String expanded = "";
                for(int i = 0; i < splitVar.length; i++)
                {
                    Process shellExec = Runtime.getRuntime().exec( new String[]{"cmd.exe", "/C", "echo", splitVar[i]});

                    java.io.BufferedReader reader = new java.io.BufferedReader( new java.io.InputStreamReader(shellExec.getInputStream()));
                    String res = reader.readLine();
                    if (res != null)
                    {
                       expanded += res;
                       if(i < splitVar.length-1)
                           expanded += java.io.File.pathSeparator;
                    }
                }

                return expanded;
            }
            catch (java.io.IOException ex)
            {
                // Crap....
                System.out.println("WARNING: Failed to evaluate environment variable contents '" + var + "'");
            }
        }
        else //We're probably on a posix system....
        {
            try
            {
                String[] splitVar = var.split(java.io.File.pathSeparator);
                String expanded = "";
                for(int i = 0; i < splitVar.length; i++)
                {
                    String v = splitVar[i];

                    String command = "eval echo \"" + v + "\"";
                    Process shellExec = Runtime.getRuntime().exec( new String[]{"bash", "-c", command});

                    java.io.BufferedReader reader = new java.io.BufferedReader( new java.io.InputStreamReader(shellExec.getInputStream()));
                    String res = reader.readLine();
                    if (res != null)
                    {
                       expanded += res;
                       if(i < splitVar.length-1)
                           expanded += java.io.File.pathSeparator;
                    }
                }

                return expanded;
            }
            catch (java.io.IOException ex)
            {
                // Crap....
                System.out.println("WARNING: Failed to evaluate environment variable contents '" + var + "' - probably due to a lack of unix...");
            }
        }

        return var;
    }

    /**
     * Tries to resolve any relative paths/symlinks on a given path
     */
    public static String expandPath(String path)
    {
        if(System.getProperty("os.name").toLowerCase().indexOf("win") == 0){
            String exp = path;
            try
            {
                Process shellExec = Runtime.getRuntime().exec("cmd.exe /C echo " + exp);

                java.io.BufferedReader reader = new java.io.BufferedReader( new java.io.InputStreamReader(shellExec.getInputStream()));
                String expandedPath = reader.readLine();
                
                // Only return a new value if expansion worked.
                // We're reading from stdin. If there was a problem, it was written
                // to stderr and our result will be null.
                if (expandedPath != null)
                {
                    exp = expandedPath;
                }
            }
            catch (java.io.IOException ex)
            {
                // Just consider it unexpandable and return original path.
                ex.printStackTrace();
                System.out.println("WARNING: Failed to expand path '" + path+ "'");
            }
            File f = new File(exp);
            path = f.getAbsolutePath();
        }
        else //We're probably on a posix system....
        {
            try
            {
                String command = "ls -d " + path;
                Process shellExec = Runtime.getRuntime().exec( new String[]{"bash", "-c", command});

                java.io.BufferedReader reader = new java.io.BufferedReader( new java.io.InputStreamReader(shellExec.getInputStream()));
                String expandedPath = reader.readLine();
                

                // Only return a new value if expansion worked.
                // We're reading from stdin. If there was a problem, it was written
                // to stderr and our result will be null.
                if (expandedPath != null)
                {
                    path = expandedPath;
                }
            }
            catch (java.io.IOException ex)
            {
                // Just consider it unexpandable and return original path.
                System.out.println("WARNING: Failed to expand path '" + path+ "' - probably due to a lack of unix...");
            }
        }
        return path;
    }

    /**
     * Utility to class to make a void print stream
     */
    static public class NullPrintStream extends PrintStream {
        public NullPrintStream(){
            super((OutputStream)null);
        }
        public void write(byte[] buf, int off, int len) {}
        public void write(int b) {}
        public void write(byte [] b) {}
    }

    /**
     * Have a list of list of strings and need the cartesian product? Look no further!
     */
    public static List<List<String>> cartesianProduct(List<List<String>> sets) {
        if (sets.size() < 2)
            return sets;
            //throw new IllegalArgumentException("Can't have a product of fewer than two sets (got " + sets.size() + ")");

        return _cartesianProduct(sets.size()-1, sets);
    }

    private static List<List<String>> _cartesianProduct(int index, List<List<String>> sets) {
        List<List<String>> ret = new ArrayList<List<String>>();
        if (index < 0 ) {
            ret.add(new ArrayList<String>());
        } else {
            for (String obj : sets.get(index)) {
                for (List<String> set : _cartesianProduct(index-1, sets)) {
                    set.add(obj);
                    ret.add(set);
                }
            }
        }
        return ret;
    }

    /**
     * Returns a string containing the arguments sorted by their name and joined together
     */
    public static String argMapToString(Map<String, String> argMap){
        ArrayList<String> argKeys = new ArrayList<String>(argMap.keySet());
        java.util.Collections.sort(argKeys);

        StringBuilder sb = new StringBuilder();
        for(String k: argKeys)
        {
            sb.append("-");
            sb.append(k);
            sb.append(" ");
            sb.append(argMap.get(k));
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Removes any arguments in the inputMap that happen to have a HIDDEN in their name
     */
    public static Map<String, String> removeHidden(Map<String, String> inputMap){
        Map<String, String> argMap = new HashMap<String, String>(inputMap);

        HashSet<String> keys = new HashSet<String>(argMap.keySet());
        for(String key : keys){
            if(key.contains("HIDDEN")){
                argMap.remove(key);
            }
        }
        return argMap;
    }

    /**
     * Gets the basename of a file
     */
    public static String removeExtension(String s)
    {

        String filename;

        // Remove the path upto the filename.
        int lastSeparatorIndex = s.lastIndexOf(System.getProperty("file.separator"));
        if (lastSeparatorIndex == -1) {
            filename = s;
        } else {
            filename = s.substring(lastSeparatorIndex + 1);
        }

        // Remove the extension.
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1)
            return filename;

        return filename.substring(0, extensionIndex);
    }

    /**
     * Joins a list of strings together sepeated by a delim in the middle
     */
    static public String joinStrings(String delim, String ... strs)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < strs.length-1; i++){
            sb.append(strs[i]);
            sb.append(delim);
        }
        sb.append(strs[strs.length-1]);
        return sb.toString();
    }
    
    /**
     * Joins a collection of strings together sepeated by a delim in the middle
     */
    static public String joinStrings(String delim, Collection<String> strs)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(String str : strs){
            if(first){
                first = false;
            }else{
                sb.append(delim);
            }
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * Looks for any strings that have spaces in them, and surrounds them with quotes
     */
    static public List<String> quoteStrings(Collection<String> oldStrs)
    {
        List<String> strs = new ArrayList<String>(oldStrs);
        for(int i = 0; i < strs.size(); i++){
            if(strs.get(i).contains(" ")){
                strs.set(i, "\"" + strs.get(i) + "\"");
            }
        }
        return strs;
    }

    /**
     * Splits a string, returning the string separated by the delimiter, with only the numLeft and numRight taken from the appropriate sides
     */
    static public List<String> splitNestedString(String params, String delimStr, int numLeft, int numRight)
    {
        ArrayList<String> args = new ArrayList<String>(numLeft);
        ArrayList<String> endArgs = new ArrayList<String>(numRight);

        //We need to rip chunks out of the left side
        int startIndex, endIndex;
        startIndex = 0;
        endIndex = 0;
        for(int i = 0; i < numLeft; i++){
            endIndex = params.indexOf(delimStr, startIndex);
            if(endIndex == -1)
                throw new IllegalArgumentException("Could only find " + i + " left args before not finding the delimeter");
            args.add(params.substring(startIndex, endIndex));
            startIndex = endIndex+delimStr.length();
        }
        int middleStart = startIndex;
        
        //Now go find stuff on the right
        endIndex = params.length();
        startIndex = endIndex;
        for(int i = 0; i < numRight; i++){
            startIndex = params.lastIndexOf(delimStr, endIndex-1);
            if(startIndex == -1)
                throw new IllegalArgumentException("Could only find " + i + " right args before not finding the delimeter");
            endArgs.add(params.substring(startIndex+delimStr.length(), endIndex));
            endIndex = startIndex;
        }
        int middleEnd = startIndex;

        if(middleStart >= middleEnd){
            throw new IllegalArgumentException("The number of left and right args doesn't leave anything in the middle");
        }

        args.add(params.substring(middleStart, middleEnd));
        for(int i = endArgs.size()-1; i >= 0; i--){
            args.add(endArgs.get(i));
        }

        return args;
    }


    /**
     * Makes a simple real valued dataset of a given size, with the class determined by index
     */
    static public Instances createDummyInstances(int numInstances, int numClasses, int numUsefulNumeric, int numUsefulCategorical, int numRandomNumeric, int numRandomCategorical, int randomCategoricalSize, int seed)
    {
        ArrayList<Attribute> attInfo = new ArrayList<Attribute>();
        ArrayList<String> classes = new ArrayList<String>();
        for(int i = 0; i < numClasses; i++){
            classes.add(Integer.toString(i));
        }

        //Make our random categorical domain
        ArrayList<String> randomCategorical = new ArrayList<String>();
        for(int i = 0; i < randomCategoricalSize; i++){
            randomCategorical.add("rcat" + Integer.toString(i));
        }

        for(int i = 0; i < numUsefulNumeric; i++){
            attInfo.add(new Attribute("usefulNumeric" + Integer.toString(i)));
        }
        for(int i = 0; i < numUsefulCategorical; i++){
            attInfo.add(new Attribute("usefulCategorical" + Integer.toString(i), classes));
        }
        for(int i = 0; i < numRandomNumeric; i++){
            attInfo.add(new Attribute("randomNumeric" + Integer.toString(i)));
        }
        for(int i = 0; i < numRandomCategorical; i++){
            attInfo.add(new Attribute("randomCategorical" + Integer.toString(i), randomCategorical));
        }
        attInfo.add(new Attribute("class", classes));

        Random rand = new Random(seed);

        Instances instances = new Instances("dummy", attInfo, numInstances);
        instances.setClassIndex(attInfo.size()-1);
        for(int j = 0; j < numInstances; j++){
            double[] features = new double[attInfo.size()];
            int classValue = j % numClasses;
        
            int index = 0;
            for(int i = 0; i < numUsefulNumeric; i++){
                features[index++] = classValue * (i+1);
            }
            for(int i = 0; i < numUsefulCategorical; i++){
                features[index++] = (classValue * (i+1)) % numClasses;
            }
            for(int i = 0; i < numRandomNumeric; i++){
                features[index++] = rand.nextGaussian();
            }
            for(int i = 0; i < numRandomCategorical; i++){
                features[index++] = rand.nextInt(randomCategoricalSize);
            }
            features[index++] = classValue;

            instances.add(new DenseInstance(1, features));
        }
        return instances;
    }

    /**
     * Gets the root Auto-WEKA location - or at least comes close.
     *
     * Prints an error message if it fails
     */
    private static boolean msFailedToFindDistributionOnce = false;
    public static String getAutoWekaDistributionPath()
    {
        String locStr = URLDecoder.decode(Util.class.getClassLoader().getResource(Util.class.getCanonicalName().replaceAll("\\.", "/") + ".class").toString());

        File dir;
        if(locStr.startsWith("jar")){
            dir = new File(locStr.substring(9,locStr.lastIndexOf("!")));
        }else{
            dir = new File(locStr);
        }

        //Walk up the path of the directory file hunting this one down
        while(dir != null){
            File paramDir = new File(dir.getAbsolutePath() + File.separator + "params");
            if(paramDir.exists() && paramDir.isDirectory())
            {
                //System.out.println("Found install dir: " + dir.getAbsolutePath());
                return dir.getAbsolutePath();
            }

            dir = dir.getParentFile();
        }
        if(!msFailedToFindDistributionOnce){
            System.out.println("Warning: Could not auto-detect the location of your Auto-WEKA install - have you moved the classes away from the 'params' diectory?");
            msFailedToFindDistributionOnce = true;
        }
        return ".";
    }
    
    /** Splits a string based on spaces, grouping atoms if they are inside non escaped double quotes.
     */
    static public List<String> splitQuotedString(String str)
    {
        List<String> strings = new ArrayList<String>();
        boolean inQuotes = false;
        boolean quoteStateChange = false;
        StringBuffer buffer = new StringBuffer();
        //Find some spaces, 
        for(int i = 0; i < str.length(); i++){
            //Have we toggled the quote state?
            char c = str.charAt(i);
            quoteStateChange = false;
            if(c == '"' && (i == 0 || str.charAt(i-1) != '\\')){
                inQuotes = !inQuotes;
                quoteStateChange = true;
            }
            //Peek at the next character - if we have a \", we need to only insert a "
            if(c == '\\' && i < str.length()-1 && str.charAt(i+1) == '"')
            {
                c = '"';
                i++;
            }

            //If we're not in quotes, and we've hit a space...
            if(!inQuotes && str.charAt(i) == ' '){
                //Do we actually have somthing in the buffer?
                if(buffer.length() > 0){
                    strings.add(buffer.toString());
                    buffer.setLength(0);
                }
            }else if(!quoteStateChange){
                //We only want to add stuff to the buffer if we're forced to by quotes, or we're not a "
                buffer.append(c);
            }
        }
        //Add on the last string if needed
        if(buffer.length() > 0){
            strings.add(buffer.toString());
        }

        return strings;
    }

    public static class ProcessKillerShutdownHook extends Thread
    {
        private Process mProc;
        public ProcessKillerShutdownHook(Process proc)
        {
            mProc = proc;
        }

        @Override
        public void run()
        {
            mProc.destroy();
        }
    }


    public static Instances loadDataSource(java.io.InputStream stream) throws Exception
    {
        ArffLoader loader = new ArffLoader();
        loader.setSource(stream);
        return loader.getDataSet();
    }

    public static void copyFile(File sourceFile, File destFile) {
        try
        {
            if(!destFile.exists()) {
                destFile.createNewFile();
            }

            FileChannel source = null;
            FileChannel destination = null;

            try {
                source = new FileInputStream(sourceFile).getChannel();
                destination = new FileOutputStream(destFile).getChannel();
                destination.transferFrom(source, 0, source.size());
            }
            finally {
                if(source != null) {
                    source.close();
                }
                if(destination != null) {
                    destination.close();
                }
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    public static Collection<String> escapeQuotes(Collection<String> collection){
      Collection<String> newCollection = new ArrayList<String>();
      for(String s : collection){
	s = s.replace("\\", "\\\\");
	s = s.replace("\"", "\\\"");
	newCollection.add(s);
      }
      return newCollection;
      
    }
}
