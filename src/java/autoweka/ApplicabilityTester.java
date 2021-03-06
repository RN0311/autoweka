package autoweka;

import weka.core.Instances;
import weka.classifiers.AbstractClassifier;
import weka.filters.Filter;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.ASEvaluation;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for determining what kind of methods can be applied to a set of instances.
 *
 * Goes through a folder looking for .param files, and trys to instantiate the class to see if 
 * it can be used with the given set of instances 
 */
public class ApplicabilityTester
{
    //What are we searching for
    private enum Testable{
        CLASSIFIER,
        ATTRIBUTE_SEARCH,
        ATTRIBUTE_EVAL,
        FILTER
    };

    public static class ApplicableClassifiers
    {
        public List<ClassParams> base;
        public List<ClassParams> meta;
        public List<ClassParams> ensemble;
    }
    
    public static class ApplicableFilters
    {
        public List<ClassParams> base;
        public List<ClassParams> meta;
    }

    /**
     * Looks inside a folder for .param files that are WEKA classifiers (base, meta and ensemble)
     *
     * @param instances The instances to test on
     * @param paramDirName The path to the folder containing the .param files
     */
    public static ApplicableClassifiers getApplicableClassifiers(Instances instances, String baseDir, List<String> allowed)
    {
        ApplicableClassifiers app = new ApplicableClassifiers();

        //First, process the base classifiers
        String dir = baseDir + File.separator + "base" + File.separator;
        app.base = getApplicable(instances, dir, Testable.CLASSIFIER, allowed, null);

        //Next, process the meta classifiers

        //First, we need to make sure that the base method is something that actually works
        ArrayList<String> options = new ArrayList<String>();
        options.add("-W");
        if (app.base.size()>0)
        	options.add(app.base.get(0).getTargetClass());
        else
        	options.add("weka.classifiers.AbstractClassifier");
        
        
        

        dir = baseDir + File.separator + "meta" + File.separator;
        app.meta = getApplicable(instances, dir, Testable.CLASSIFIER, allowed, options);

        //And do all the ensemble methods
        options.clear();
        options.add("-W");
        if (app.base.size()>0)
        	options.add(app.base.get(0).getTargetClass());
        else
        	options.add("weka.classifiers.AbstractClassifier");

        dir = baseDir + File.separator + "ensemble" + File.separator;
        app.ensemble = getApplicable(instances, dir, Testable.CLASSIFIER, allowed, options);

        return app;
    }
    
    /**
     * Looks inside a folder for .param files that are WEKA filters (base and meta)
     *
     * @param instances The instances to test on
     * @param paramDirName The path to the folder containing the .param files
     */
    public static ApplicableFilters getApplicableFilters(Instances instances, String baseDir, List<String> allowed)
    {
    	ApplicableFilters app = new ApplicableFilters();

        //First, process the base classifiers
        String dir = baseDir + File.separator + "baseFilters" + File.separator;
        app.base = getApplicable(instances, dir, Testable.FILTER, allowed, null);

        //Next, process the meta filters

        //First, we need to make sure that the base method is something that actually works
        ArrayList<String> options = new ArrayList<String>();
        options.add("-F");
        if (app.base.size()>0)
        	options.add(app.base.get(0).getTargetClass());
        else
        	options.add("weka.filters.AllFilter");

        dir = baseDir + File.separator + "metaFilters" + File.separator;
        app.meta = getApplicable(instances, dir, Testable.FILTER, allowed, options);


        return app;
    }
    
    /**
     * Looks inside a folder for .param files that are WEKA ASEvals
     *
     * @param instances The instances to test on
     * @param paramDirName The path to the folder containing the .param files
     */
    public static List<ClassParams> getApplicableAttributeEvaluators(Instances instances, String paramDirName)
    {
        return getApplicable(instances, paramDirName + File.separator + "attribselection" + File.separator + "eval" + File.separator, Testable.ATTRIBUTE_EVAL, null, null);
    }
    
    /**
     * Looks inside a folder for .param files that are WEKA ASSearches
     *
     * @param instances The instances to test on
     * @param paramDirName The path to the folder containing the .param files
     */
    public static List<ClassParams> getApplicableAttributeSearchers(Instances instances, String paramDirName)
    {
        return getApplicable(instances, paramDirName + File.separator + "attribselection" + File.separator + "search" + File.separator, Testable.ATTRIBUTE_SEARCH, null, null);
    }

    private static List<ClassParams> getApplicable(Instances instances, String paramDirName, Testable type, List<String> allowed, List<String> options)
    {
        File paramDir = new File(paramDirName);
        if(!paramDir.exists() || !paramDir.isDirectory())
        {
            throw new RuntimeException(paramDirName + " is not a valid directory");
        }
        FileFilter paramFilter = new FileFilter() {
            public boolean accept(File file) {
                //Go find param files, and ignore stuff that vim leaves behind when I'm editing it..
                return file.getName().endsWith(".params") && !file.getName().startsWith(".");
            }
        };

        ArrayList<String> names = new ArrayList<String>();
        File[] params = paramDir.listFiles(paramFilter);
        for(File i:params)
        {
           names.add(Util.removeExtension(i.getName()));
        }
        java.util.Collections.sort(names);

        ArrayList<ClassParams> goodMethods = new ArrayList<ClassParams>();

        for(String name: names)
        {
            if(allowed != null && !allowed.contains(name))
                continue;
            if(isApplicable(name, instances, options, type))
            {
                goodMethods.add(new ClassParams(paramDirName + File.separatorChar + name + ".params"));
            }
        }
        return goodMethods;
    }

    private static boolean isApplicable(String method, Instances instances, List<String> options, Testable type)
    {
        switch(type){
            case CLASSIFIER:
                return isApplicableClassifier(method, instances, options);
            case ATTRIBUTE_EVAL:
                return isApplicableAttributeEvaluator(method, instances);
            case ATTRIBUTE_SEARCH:
                return isApplicableAttributeSearch(method, instances);
            case FILTER:
            	return isApplicableFilter(method, instances);
            default:
                return false;
        }
    }
    
    private static boolean isApplicableClassifier(String method, Instances instances, List<String> options)
    {
        //Go get ourselves a classifier, and get some 
        try
        {
            Class<?> cls = Class.forName(method);
            AbstractClassifier classifier = (AbstractClassifier)cls.newInstance();
            if(options != null){
                try{
                    classifier.setOptions(options.toArray(new String[options.size()]));
                }catch (Exception e) {
                    throw new RuntimeException("Failed to set options during applicability testing", e);
                }
            }
//            if (method.equals("weka.classifiers.meta.MyFilteredClassifier"))
//            	System.out.println("Testing my classifier");
            classifier.getCapabilities().testWithFail(instances);
            return true;
        }
        catch(ClassNotFoundException e)
        {
            System.out.println("No class '" + method + "' found");
        }
        catch(InstantiationException e)
        {
            System.out.println("Failed to instantiate '" + method + "': " + e.getMessage());
        }
        catch(IllegalAccessException e)
        {
            System.out.println("Illegal access exception creating '" + method + "': " + e.getMessage());
        }
        catch(weka.core.UnsupportedAttributeTypeException e)
        {
            System.out.println(method + " failed with message: " + e.getMessage());
        }
        catch(Exception e)
        {
            //e.printStackTrace();
            System.out.println("'" + method + "' not supported");
        }
        return false;
    }

    private static boolean isApplicableAttributeSearch(String method, Instances instances){
        //Build an AS...Search  (curse you bad naming scheme!) and see if it can handle the instances
        try{
            ASSearch search = ASSearch.forName(method, new String[0]);
        } catch(weka.core.UnsupportedAttributeTypeException e) {
            System.out.println(method + " failed with message: " + e.getMessage());
            return false;
        }catch(Exception e){
            e.printStackTrace();
            System.out.println("'" + method + "' not supported");
            return false;
        }
        return true;
    }
    
    private static boolean isApplicableAttributeEvaluator(String method, Instances instances){
        //Build an ASE and see if it can handle the instances
        try{
            ASEvaluation eval = ASEvaluation.forName(method, new String[0]);
            eval.getCapabilities().testWithFail(instances);
        } catch(weka.core.UnsupportedAttributeTypeException e) {
            System.out.println(method + " failed with message: " + e.getMessage());
            return false;
        }catch(Exception e){
            e.printStackTrace();
            System.out.println("'" + method + "' not supported");
            return false;
        }
        return true;
    }
    
    private static boolean isApplicableFilter(String method, Instances instances)
    {
        try
        {
            Class<?> cls = Class.forName(method);
            Filter filter = (Filter)cls.newInstance();

            filter.getCapabilities().testWithFail(instances);
            return true;
        }
        catch(ClassNotFoundException e)
        {
            System.out.println("No class '" + method + "' found");
        }
        catch(InstantiationException e)
        {
            System.out.println("Failed to instantiate '" + method + "': " + e.getMessage());
        }
        catch(IllegalAccessException e)
        {
            System.out.println("Illegal access exception creating '" + method + "': " + e.getMessage());
        }
        catch(weka.core.UnsupportedAttributeTypeException e)
        {
            System.out.println(method + " failed with message: " + e.getMessage());
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.out.println("'" + method + "' not supported");
        }
        return false;
    }

}
