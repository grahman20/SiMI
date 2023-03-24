/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package simi;
import Jama.Matrix;
import SysFor.*;
import java.io.*;
import java.util.*;
/**
 * SiMI imputes numerical and categorical missing values by making an educated guess 
 * based on records that are similar to the record having a missing value. 
 * Using the similarity and correlations, missing values are then imputed. 
 * To achieve a higher quality of imputation some segments are merged together using a novel approach.
 * 
 * <h2>Reference</h2>
 * 
 * Rahman, M. G. and Islam, M. Z. (2013): Missing Value Imputation Using Decision Trees and Decision Forests by Splitting and Merging Records: Two Novel Techniques. Knowledge-Based Systems, Vol. 53, pp. 51 - 65, ISSN 0950-7051, DOI information: http://dx.doi.org/10.1016/j.knosys.2013.08.023
 *  
 * @author Md Geaur Rahman <https://csusap.csu.edu.au/~grahman/>
 */
/**
 *
 * @author Geaur Rahman
 * 19/05/2011
 */
public class SiMI {

    private double distWeight=0.7;
    private double correlWeight;
    private String treeSepLiteral="DT-GEA";//used to seperate two tree in the output (rules) file
    private int lamda_threshold=25;//user-defined threshold to identify minimum sized intersection
    private int MinimumLeaf=5;//Minimumno. of leaves at the cross section
    private Set []LeafRecords; //contains record id of each leaf
    private Set missingRecordId; //contains id of the records having missing values
    private int []LeafIdOfMissingRec; //contains leaf id of each record having missing values
    private int []LeafPosOfMissingRec; //contains position of each record (having missing values) in a leaf
    private int noOfTree;//contains no. of tree created for the dataset
    private String []logicRuleForEachLeaf;//contains logic rules for each leaf
    private String []majorityClassValueOfEachLeaf;//contains the majority class value of each leaf
    private int totalLeaf;//contains total no. of leaf
    
    int []leafStart;//contains starting posing of each tree in the leaf file
    int []leafLength;//contains file name for each leaf
    private int totalAttributes; // total no. of attributes of the data file
    private int totalNumericalAttributes; // total no. of numerical attributes of the data file
    private int []listOfNumericalAttr;//List of numerical attributes
    private int [] attributeType; //contain attributes type 0->Categorical, 1->Numerical, 2->Class
    private String [] attrSType; //contain attributes type C->Categorical, N->Numerical
    private String [][]dataset;//contains the whole data set
    private String [][]datasetNormalized;//contains the normmalized data set
    private String [][]datasetCat;//contains only categorical attributes data
    private String []LeafFileName;//contains file name of each leaf
    private String []LeafFileImputed;//contains the imputed file name of each leaf
    private int []delta; //0->missing, 1-> NOT missing
    private int []domainsize;//contains domain size of each attribute
    private String [][] domainValue; //contains domain value of each attribute
    private String [][]MeanMode; //Atrribute Mean/Mode of each leaf
    private int maxDS;//maximum domain size
    private Set LeafRecord; //contains record id of each leaf
    private String []domainValue1;//contains domain values of all attributes
    private int []start1;//strating position of each attribute
    private int []domainsize1;//contains domain size of each attribute
    private double [][]S;//contains total degrees/edges of each category

    /**
     * Impute a given data set having missing values
     * and calls other necessary methods
     *
     * @param attrFile contains 2 lines attributes types and name information
     * @param dataFile data set having missing values to be imputed
     * @param outputFile filename of the imputed data set
     */
    public void runSiMI(String attrFile, String dataFile,String outputFile, double lambda)
    {
        distWeight=lambda;
        correlWeight=1.0-distWeight;
        getAttrType(attrFile);
        FileManager fileManager = new FileManager();
        dataset=fileManager.readFileAs2DArray(new File(dataFile));
        String []tmpFiles=new String[5];
        int tmpf=0;
        ////calculate similarity of categorical attribute values
        catDataset();
        Similarity sm=new Similarity();
        S=sm.similarityMeasure(datasetCat);
        start1=sm.getStart();
        domainsize1=sm.getDomainSize();
        domainValue1=sm.getDominValues();
        ////normalize data set
        String normalizeFile = fileManager.changedFileName(dataFile, "_normalize");//no missing
        Module mdl=new Module();
        mdl.normaliseFile(dataFile, normalizeFile,attrSType,0,0.0,1.0);
        datasetNormalized=fileManager.readFileAs2DArray(new File(normalizeFile));
        tmpFiles[tmpf]=normalizeFile;tmpf++;

         /*
          * Step 1: Divide data set into Dc and Di
          */
       
        String DcFile = fileManager.changedFileName(dataFile, "_Dc");//no missing
        String DiFile = fileManager.changedFileName(dataFile, "_Di"); //all missing records
        fileManager.divideDataset(new File(dataFile),DcFile, DiFile);
        tmpFiles[tmpf]=DcFile;tmpf++;
        tmpFiles[tmpf]=DiFile;tmpf++;
         /*
         * Step 2: Call Sysfor to build k number of trees, say k=3
         */
        String rulesFile = fileManager.changedFileName(dataFile, "_rule");
        generateMultipleTrees(attrFile,DcFile,rulesFile);
        tmpFiles[tmpf]=rulesFile;tmpf++;


        assignRecordsIntoLeaf(dataFile);  //assign all records in to leaves
        
        if(missingRecordId.size()>0)
        {
        /*
         * Step 3: Cross intersection among leaves of different trees and
         * generate a final tree having L leaves.
         */
         if(noOfTree>1)
         {
         cross_Section();
        
         /*
          * Step 4: Find the low sized intersections (if any)
          * and merge each of them with an intersection having (resultant)
          * maximum correlation.
          */
         findAndMergeLowSizedLeaf();
        }
          if(totalLeaf==0)
          {
              ifNoTree(dataFile);
          }
          
         /*
          * step 5: Impute numerical missing values using EMI algorithm and
          * impute categorical missing values using attribute mode value
          */
          int []missRecList=toIntArray(missingRecordId);
          generateLeafFileAndImpute(attrFile,dataFile,missRecList);
          calculateFrequency();
          Imputation(dataFile,outputFile);
          /*
           * remove temporary files
           */
          fileManager.removeListOfFiles(LeafFileName, totalLeaf);
          fileManager.removeListOfFiles(LeafFileImputed, totalLeaf);
        
        
        }
        else{
            System.out.println( "The data file "+dataFile+" does not have any missing values");
        }
        //remove tmp files
        fileManager.removeListOfFiles(tmpFiles, tmpf);
     }


    /*
 * Normalize an array
 */
private void normalize(double []Val, int N, int sI)
{
    double lb=0.0, ub=1.0, min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
     for(int i=0;i<N;i++)
        {
            if(i!=sI)
            {
                if(Val[i]>max) max=Val[i];
                if(Val[i]<min) min=Val[i];
            }
        }
    double dnom=max-min+lb;
    for(int i=0;i<N;i++)
        {
            if(i!=sI)
            {
                Val[i]=((Val[i]-min+lb)/dnom)*ub;
            }
        }
}
    private void catDataset()
    {
        int totCatAtr=  totalAttributes-totalNumericalAttributes;
        datasetCat=new String[dataset.length][totCatAtr];
        for(int i=0; i<dataset.length;i++)
        {
            int k=0;
            for (int j = 0; j < totalAttributes; j++)
             {
                if(attributeType[j]!=1)
                {
                    datasetCat[i][k]=dataset[i][j];k++;
                }
             }
        }
    }

  
    /*
     * Weighted imputation of numerical and categorical missing values
     * and write them to the output file
     *
     * @param dataFile data set having missing values to be imputed
     * @param outputFile filename of the imputed data set
     */
    private void Imputation(String dataFile,String outputFile)
    {
        FileManager fileManager = new FileManager();
        String []dataArray=fileManager.readFileAsArray(new File(dataFile));
        File outF=new File(outputFile);
        for(int i=0; i<dataArray.length;i++)
        {
            String rec=dataArray[i];
            if(delta[i]==0)
            {
                rec="";
                for(int j=0; j<totalAttributes;j++)
                {
                    String tmp=dataset[i][j];
                    if(isMissing(tmp)==1)
                    {
                        if(attributeType[j]==1)
                        {
                            tmp=ImputeNumerical(i,j);
                        }
                        else
                        {
                            tmp=ImputeCategorical(i,j);
                        }

                    }
                    rec+=tmp+",";
                    
                }

            }
            rec=rec+"\n";
            if(i==0)
                fileManager.writeToFile(outF, rec);
            else
                fileManager.appendToFile(outF, rec);
        }
        
    }

   

/**
    * Impute numerical attributes
    */
    private String ImputeNumerical(int row, int col)
    {
        FileManager fileManager = new FileManager();
        String ret="";
        int []leafList=findLeaves(row);
        int noL=leafList.length;double c=0.0; double sum=0.0;
        for(int i=0;i<noL;i++)
        {
           int []tmpArry=toIntArray(LeafRecords[leafList[i]]);
           int g=findMissingRecordId(row,tmpArry);
           if(g>-1)
            {
            String[][]tmpA=fileManager.readFileAs2DArray(new File(LeafFileImputed[leafList[i]]));
            ret=tmpA[g][col];
            if(isMissing(ret)==0)
            {
               if(Double.parseDouble(ret)<0)
                    ret=MeanMode[leafList[i]][col];
            }
            else
            {
                ret=MeanMode[leafList[i]][col];

            }
             sum+=Double.parseDouble(ret);c+=1.0;
            }
        }
        if (c>0)ret=(sum/c)+"";
        return ret;
    }

    private int []findLeaves(int mRecord)
    {
        Set lList=new TreeSet<Integer>();
        for (int i=0;i<totalLeaf;i++)
        {
          if(LeafRecords[i].contains(mRecord))
          {
              lList.add(i);
            }
        }

        int []tmpAry=toIntArray(lList);
        return tmpAry;
    }

  
/**
    * Impute numerical attributes
    */
    private String ImputeCategorical(int row, int col)
    {
        String ret="";
        int []leafList=findLeaves(row);
        int noL=leafList.length;
        String []StrV=new String[noL];
        int []f=new int [noL];int dv=0;
        for(int i=0;i<noL;i++)
        {
           f[i]=0;
           int []tmpArry=toIntArray(LeafRecords[leafList[i]]);
           int g=findMissingRecordId(row,tmpArry);
           if(g>-1)
            {
                String tmp=MeanMode[leafList[i]][col];
                int fg=0;
                for(int k=0;k<dv;k++)
                {
                    if(StrV[k].equals(tmp))
                    {
                        fg=k; break;
                    }
                }
                if(fg==0)
                {
                    StrV[dv]=tmp;dv++; f[i]++;
                }
                 else{
                     f[fg]++;
                 }
            }
        }
        int max=0,index=0;
         for(int i=0;i<dv;i++)
         {
             if(max<f[i])
             {
                 max=f[i];index=i;
             }
         }
        ret=StrV[index];
        return ret;
    }
   /**
    * Calculate frequency
    */
    private void calculateFrequency()
    {
        FileManager fileManager = new FileManager();
        MeanMode=new String[totalLeaf][totalAttributes];
        for(int i=0; i<totalLeaf;i++)
        {
             String[][]tmpA=fileManager.readFileAs2DArray(new File(LeafFileImputed[i]));
             for(int j=0;j<totalAttributes;j++)
             {
                 if(attributeType[j]==1)
                 {
                     MeanMode[i][j]=calMean(tmpA,j)+"";
                 }
                 else
                 {
                     MeanMode[i][j]=calMode(tmpA,j);
                 }
             }
            
        }
        
    }
/*
 * Calculate attribute mean of a leaf
 */

 private double calMean(String [][]dataF, int col)
 {
     double avg=0.0, sum=0.0;
     for(int i=0; i<dataF.length;i++)
     {
         if(isMissing(dataF[i][col])==0)
           {
             sum+=Double.parseDouble(dataF[i][col]);
            }
      }
     if (dataF.length>0)avg=sum/dataF.length;
     return avg;
 }

 /*
 * Calculate attribute mode of a leaf
 */

 private String calMode(String [][]dataF, int col)
 {
     String ret="?";
     int fg[]=new int[domainsize[col]];
     for(int t=0; t<domainsize[col];t++)
         fg[t]=0;
     for(int i=0; i<dataF.length;i++)
     {
         if(isMissing(dataF[i][col])==0)
           {
             for(int t=0; t<domainsize[col];t++)
                {
                    if(domainValue[col][t].equals(dataF[i][col]))
                    {
                        fg[t]++;  break;
                    }
                }
            }
      }
     int v=0;
     for(int t=0; t<domainsize[col];t++)
         if(fg[t]>v)
         {
             v=fg[t];
             ret=domainValue[col][t];
         }
     
     return ret;
 }


/**
 * Impute numerical missing values using EMI algorithm and
 * impute categorical missing values using attribute mode value
 *
 * @param attrFile contains 2 lines attributes types and name information
 * @param dataFile data set having missing values to be imputed
 */
private void generateLeafFileAndImpute(String attrFile,String dataFile,int []missRecList)
{
    FileManager fileManager = new FileManager();
    String []dataArray=fileManager.readFileAsArray(new File(dataFile));
    totalLeaf=LeafRecords.length;
    LeafRecords[totalLeaf-1].addAll(LeafRecord);
    LeafFileName=new String[totalLeaf];
    LeafFileImputed=new String[totalLeaf];
    int rid;
    for(int i=0;i<totalLeaf;i++)
    {
        LeafFileName[i]=fileManager.changedFileName(dataFile, "Leaf"+i);
        LeafFileImputed[i]=fileManager.changedFileName(dataFile, "LeafImp"+i);
        File outF=new File(LeafFileName[i]);
        int []tmpList=toIntArray(LeafRecords[i]);
        int numOfMissingRec=0;
        for(int k=0;k<tmpList.length;k++)
        {
            rid= tmpList[k];
            int g=findMissingRecordId(rid,missRecList);
            if(g>-1)
            {
               numOfMissingRec++;
            }
            if(k==0)
            {
                fileManager.writeToFile(outF, dataArray[rid]+"\n");
            }
            else{
                fileManager.appendToFile(outF, dataArray[rid]+"\n");
            }
        }
        if(numOfMissingRec>0)
        {
            //call EMI to impute numerical missing values
            mviEMI emi=new mviEMI();
            emi.emImplementation(attrFile, LeafFileName[i], LeafFileImputed[i],0,attrSType,-1);
        }
        else
        {
            fileManager.copyFile(LeafFileName[i], LeafFileImputed[i]);
         }

  }

}
/*
 * find the record position having missing value/s
 */

private int findMissingRecordId(int rid,int []tmpList)
{
    int index=-1;
    for(int k=0;k<tmpList.length;k++)
        {
            if(rid== tmpList[k])
            {
                index=k;break;
            }
        }
    return index;

}


        /**
          * Find the low sized intersections (if any)
          * and merge each of them with an intersection having (resultant)
          * maximum correlation.
          */
private void findAndMergeLowSizedLeaf()
{
    Set []tmpLeafRecords; 
    int smallest_Leaf=smallestLeaf();
    while(LeafRecords[smallest_Leaf].size()<lamda_threshold && totalLeaf>MinimumLeaf)
    {
        //find which leaf (resultant) has maximum correlation value
        double max_correl=Double.NEGATIVE_INFINITY;
        int max_corIndex=Integer.MAX_VALUE;
        double []distV=new double [totalLeaf];
        double []correlV=new double [totalLeaf];

        int []leafX=toIntArray(LeafRecords[smallest_Leaf]);
        for(int i=0;i<totalLeaf;i++)
        {
            if(i!=smallest_Leaf)
            {
                int []leafY=toIntArray(LeafRecords[i]);
                distV[i]= distBetweenTwoLeaves(leafX,leafY);
                correlV[i]= findCorrelation(i,smallest_Leaf);
                double tmp=correlV[i]*correlWeight+(1.0-distV[i])*distWeight;
                if(tmp>max_correl)
                  {
                    max_correl=tmp;max_corIndex=i;
                  }
            }
        }
        //merge two leaves and reorganized the list
        LeafRecords[max_corIndex].addAll(LeafRecords[smallest_Leaf]);
        int tmpSize=totalLeaf-1;
        tmpLeafRecords=new TreeSet[tmpSize];
        int k=0;
        for(int z=0;z<totalLeaf;z++)
        {
            if(z!=smallest_Leaf)
            {
                tmpLeafRecords[k]=new TreeSet<Integer>();
                tmpLeafRecords[k].addAll(LeafRecords[z]);
                k++;
            }
        }
        totalLeaf=tmpSize;
        LeafRecords=new TreeSet[tmpSize];
        for(int z=0;z<totalLeaf;z++)
        {
            LeafRecords[z]=new TreeSet<Integer>();
            LeafRecords[z].addAll(tmpLeafRecords[z]);
         }
        
        //Again, find the leaf which is the smallest in size
        smallest_Leaf=smallestLeaf();
        
    }
}


/**
 * Find correlation of an intersection which is the merged of two intersections.
 */
private double findCorrelation(int curIndex, int sIndex)
{
    
    Set mergedLeaves=new TreeSet<Integer>();
    mergedLeaves=union(LeafRecords[curIndex],LeafRecords[sIndex]);
    int []array=toIntArray(mergedLeaves);
    int tmpSize=array.length;
    double [][]dataNum=new double[tmpSize][totalNumericalAttributes];
    String str="";
    for(int i=0;i<tmpSize;i++)
    {
        for(int j=0; j<totalNumericalAttributes; j++)
        {
            str=dataset[array[i]][listOfNumericalAttr[j]];
            if(isMissing(str)==1)
                dataNum[i][j]=0.0;
            else
                dataNum[i][j]=Double.parseDouble(str);
         }
    }

    //find correlation
    double [][]corNum=new double[totalNumericalAttributes][totalNumericalAttributes];
    MatrixCalculation mxCal=new MatrixCalculation();
    mxCal.computeCorrelation(dataNum,tmpSize, totalNumericalAttributes, corNum);
    return calNorm2(corNum,totalNumericalAttributes);
}

/**
 * Find correlation of an intersection which is the merged of two intersections.
 */
private double findCorrelationt(int curIndex, int sIndex)
{
    Set tmleaf=new TreeSet<Integer>();
    tmleaf.add(curIndex);
    Set mergedLeaves=new TreeSet<Integer>();
    mergedLeaves=union(tmleaf,LeafRecords[sIndex]);
    int []array=toIntArray(mergedLeaves);
    int tmpSize=array.length;
    double [][]dataNum=new double[tmpSize][totalNumericalAttributes];
    String str="";
    for(int i=0;i<tmpSize;i++)
    {
        for(int j=0; j<totalNumericalAttributes; j++)
        {
            str=dataset[array[i]][listOfNumericalAttr[j]];
            if(isMissing(str)==1)
                dataNum[i][j]=0.0;
            else
                dataNum[i][j]=Double.parseDouble(str);
         }
    }

    //find correlation
    double [][]corNum=new double[totalNumericalAttributes][totalNumericalAttributes];
    MatrixCalculation mxCal=new MatrixCalculation();
    mxCal.computeCorrelation(dataNum,tmpSize, totalNumericalAttributes, corNum);
    return calNorm2(corNum,totalNumericalAttributes);
}

/**
 * Find correlation of an intersection which is the merged of two intersections.
 */
private void calCorrelation(int curIndex)
{

    Set mergedLeaves=new TreeSet<Integer>();
    mergedLeaves.addAll(LeafRecords[curIndex]);
    int []array=toIntArray(mergedLeaves);
    int tmpSize=array.length;
    double [][]dataNum=new double[tmpSize][totalNumericalAttributes];
    String str="";
    for(int i=0;i<tmpSize;i++)
    {
        for(int j=0; j<totalNumericalAttributes; j++)
        {
            str=dataset[array[i]][listOfNumericalAttr[j]];
            if(isMissing(str)==1)
                dataNum[i][j]=0.0;
            else
                dataNum[i][j]=Double.parseDouble(str);
         }
    }

    //find correlation
    double [][]corNum=new double[totalNumericalAttributes][totalNumericalAttributes];
    MatrixCalculation mxCal=new MatrixCalculation();
    mxCal.computeCorrelation(dataNum,tmpSize, totalNumericalAttributes, corNum);
    Matrix cNUm=new Matrix(corNum);
    cNUm.print(4,2);
    System.out.println("Norm2: "+ calNorm2(corNum,totalNumericalAttributes));
    
}
/*
 * Calculate a single value from a matrix
 */
private double calNorm2(double [][]corNum,int N)
  {
    double ret=0.0, sum=0.0;

    for(int i=0;i<N;i++)
        for(int j=i+1;j<N;j++)
            sum+=Math.pow(corNum[i][j],2);
    if(sum>0.0)
    {
        ret=Math.sqrt(sum)/(N*N);
    }
    return ret;
}

/**
 * Convert a set to an integer array
 */
private int []toIntArray(Set <Integer>setA)
 {
    Object[] arrObj = setA.toArray();
    String tmp;
    int []arrInt=new int[arrObj.length];
    for (int i = 0; i < arrObj.length; i++)
    {
        tmp=arrObj[i]+"";
        arrInt[i]=Integer.parseInt(tmp);
     }
    return arrInt;

}

/**
 * Find the leaf which is the smallest in size
 */
private int smallestLeaf()
 {
    int lowindex=Integer.MAX_VALUE;
    int min=Integer.MAX_VALUE;
    for(int i=0;i<totalLeaf;i++)
    {
        int tmp=LeafRecords[i].size();
        if(tmp<min)
        {
            min=tmp;
            lowindex=i;
        }
    }
    return lowindex;
}
 /**
     * Cross intersection among leaves of different trees and
     * generate a final tree having L leaves.
     */
    private void cross_Section()
    {
        Set []tmpLeafRecords;
        Set []intersectLeafRecords;
        int intersectLeaf=0, L=0;
        int tmpLeaf=leafLength[0];
        intersectLeafRecords=new TreeSet[tmpLeaf];
        for(int j=0;j<tmpLeaf;j++)
            {
                intersectLeafRecords[j]=new TreeSet<Integer>();
                intersectLeafRecords[j].addAll(LeafRecords[j]);
            }
        for(int p=1;p<noOfTree;p++)
        {
            int z=tmpLeaf;
            tmpLeafRecords=new TreeSet[z];
            for(int j=0;j<z;j++)
            {
                tmpLeafRecords[j]=new TreeSet<Integer>();
                tmpLeafRecords[j].addAll(intersectLeafRecords[j]);
            }
            int w=leafLength[p];
            L=0;
            intersectLeaf=z*w;
            intersectLeafRecords=new TreeSet[intersectLeaf];
            for(int j=0;j<z;j++)
            {
                for(int k=leafStart[p];k<w+leafStart[p];k++)
                {
                    Set gtmpIS=new TreeSet<Integer>();
                    gtmpIS=intersection(tmpLeafRecords[j], LeafRecords[k]);
                    if(gtmpIS.size()>0)
                    {
                        intersectLeafRecords[L]=new TreeSet<Integer>();
                        intersectLeafRecords[L].addAll(gtmpIS);
                        L++;
                    }
                }
            }
            tmpLeaf=L;
        }

        //get final leaves
        totalLeaf=tmpLeaf;
        LeafRecords=new TreeSet[totalLeaf];
        for(int j=0;j<totalLeaf;j++)
        {
            LeafRecords[j]=new TreeSet<Integer>();
            LeafRecords[j].addAll(intersectLeafRecords[j]);
        }



  }


    /*
     * Find intersection of two sets A and B
     */
    
  public static <T> Set<T> intersection(Set<T> setA, Set<T> setB) {
    Set<T> tmp = new TreeSet<T>();
    for (T x : setA)
      if (setB.contains(x))
        tmp.add(x);
    return tmp;
  }
    /*
     * Find union of two sets A and B
     */
  public static <T> Set<T> union(Set<T> setA, Set<T> setB) {
    Set<T> tmp = new TreeSet<T>(setA);
    tmp.addAll(setB);
    return tmp;
  }
  /**
     * Find difference of two sets A and B
     */
  public static <T> Set<T> difference(Set<T> setA, Set<T> setB) {
    Set<T> tmp = new TreeSet<T>(setA);
    tmp.removeAll(setB);
    return tmp;
  }
    /**
     * Build multiple trees on a given data set
     *
     * @param attrFile contains 2 lines attributes types and name information
     * @param dataFile data set to be used to build multiple trees
     * @param outputFile contains the generated rules of multiple trees
     */
    private void generateMultipleTrees(String attrFile, String dataFile,String outputFile)
    {
        FileManager fileManager = new FileManager();
        //Generate name file from Dc
        String namesFile = fileManager.changedFileName(dataFile, "_name");
        String gtest=fileManager.extractNameFileFromDataFile(new File(attrFile),
            new File(dataFile),new File(namesFile));
        
        initializeDomainSize(namesFile);
        //creating decision tree
         DecisionTreeBuilder treeBuilder = new DecisionTreeBuilder(namesFile, dataFile, outputFile, DecisionTreeBuilder.SEE5);
         treeBuilder.createMultipleTrees();

        //retrieve logic rules into arrays
        totalLeaf=0;

        String []ruleArray=fileManager.readFileAsArray(new File(outputFile));
        int ruleLen=ruleArray.length;
        int ntr=0;
        String rule="", tmp="";
        if(ruleLen>0)
        {
            for(int i=1;i<ruleLen;i++)
            {
                tmp=ruleArray[i]+"\n";
                if(ruleArray[i].equals(treeSepLiteral))
                    {
                        int t=i+2;
                        while( t<ruleLen && ruleArray[t].equals(treeSepLiteral) )
                        {
                            t+=2;
                        }
                        i=t-1;
                        if(!ruleArray[i].equals("") && i<ruleLen-1)
                        {
                            ntr++;
                        }
                        else
                        {
                           tmp="";
                        }
                    }
                if(!ruleArray[i].equals(""))
                {
                    rule=rule+tmp;
                    
                }
                    
            }
            rule=ntr+"\n"+rule;
            fileManager.writeToFile(new File(outputFile), rule);
        }
       
        ruleArray=fileManager.readFileAsArray(new File(outputFile));
        ruleLen=ruleArray.length;
        if(ruleLen>0)
        {
            noOfTree=Integer.parseInt(ruleArray[0]);
            if(noOfTree>0)
            {
                for(int i=2;i<ruleLen;i++)
                {
                    if(!ruleArray[i].equals(treeSepLiteral))
                    {
                        totalLeaf++;                        
                    }

                }
                int nt=0, tl=0,ntl=0;
                leafStart=new int[noOfTree];
                leafLength=new int[noOfTree];
                logicRuleForEachLeaf=new String[totalLeaf];
                majorityClassValueOfEachLeaf=new String[totalLeaf];
                for(int i=2;i<ruleLen;i++)
                {
                    if(ruleArray[i].equals(treeSepLiteral) || i==ruleLen-1)
                    {
                        leafLength[nt] = i==ruleLen-1?ntl+1:ntl ;
                        leafStart[nt]= nt==0? 0: leafStart[nt-1]+leafLength[nt-1];
                        ntl=0;
                        nt++;
                        if(i!=ruleLen-1)i+=1;
                    }
                    if(!ruleArray[i].equals(treeSepLiteral))
                    {
                        logicRuleForEachLeaf[tl]=ruleArray[i];
                        majorityClassValueOfEachLeaf[tl]=
                                findMajorityClassValues(attributeType,totalAttributes,ruleArray[i]);
                        tl++;ntl++;
                     }

                }

            }
        }
        //remove tmp file
        fileManager.removeFile(namesFile);

    }

 /**
  * initialize domain size and values
  */
private void initializeDomainSize(String nFile)
{
    FileManager fileManager = new FileManager();
    String [] nFileT = fileManager.readFileAsArray(new File(nFile));
    int l=nFileT.length;
    int max=0, i,j;
    StringTokenizer tokenizer;
    domainsize=new int[totalAttributes];
    for(i=2;i<l;i++)
    {
        tokenizer = new StringTokenizer(nFileT[i], " ,\t\n\r\f");
        String aty=tokenizer.nextToken();
        if(aty.equals("c"))
        {
            aty=tokenizer.nextToken();
            domainsize[i-2]=Integer.parseInt(tokenizer.nextToken());
            if(domainsize[i-2]>max)max=domainsize[i-2];
        }
        
    }
   maxDS=max;
   domainValue=new String[totalAttributes][maxDS];
   for(i=2;i<l;i++)
    {
        tokenizer = new StringTokenizer(nFileT[i], " ,\t\n\r\f");
        String aty=tokenizer.nextToken();
        if(aty.equals("c"))
        {
            aty=tokenizer.nextToken();
            aty=tokenizer.nextToken();
            for(j=0;j<domainsize[i-2];j++)
                domainValue[i-2][j]=tokenizer.nextToken();
        }
    }
}


    /**
     * Assign a record (no missing) into a leaf where it belongs to.
     *
     * @param dataFile the records of a given data set to be assigned into leaves
     */
    private void assignRecordsIntoLeaf(String dataFile)
    {
        FileManager fileManager = new FileManager();
         //retrieve logic rules into arrays
        LeafRecords=new TreeSet[totalLeaf];
        LeafRecord=new TreeSet<Integer>();
        missingRecordId=new TreeSet<Integer>();
        for(int j=0;j<totalLeaf;j++)
        {
            LeafRecords[j]=new TreeSet<Integer>();
        }
        String []dataArray=fileManager.readFileAsArray(new File(dataFile));
        int ruleRec[]=new int[totalLeaf];
        delta=new int[dataArray.length];

        for(int i=0;i<dataArray.length;i++)
        {
            delta[i]=1;LeafRecord.add(i);
            int tmp=isRecMissing(i);
            if(tmp==1)
                {missingRecordId.add(i); delta[i]=0;}
// else
//            {
            for(int z=0;z<totalLeaf;z++)ruleRec[z]=0;
            findRules(i,ruleRec,1);

            for(int k=0;k<noOfTree;k++)
            {
                int fg=0;
                for(int j=leafStart[k]; j<leafLength[k]+leafStart[k];j++)
                {
                    if(ruleRec[j]==1)
                    {
                        LeafRecords[j].add(i);fg++;
                        //break;
                    }
                }
            }
        }
//        }
    }

    /**
     * Find the rules that satisfy  a record
     *
     * @param rec for which the method will find a number rules that satisfy the record
     * @Rules contains 0/1, 1->if the record satisfies a rule, otherwise 0.
     */
    private void findRules(int recId, int []Rules, int isMis)
    {
        if(isMis==0) //if the record has no missing value
        {
            for(int j=0;j<totalLeaf;j++)
           {
               if(isThisRecSatisfyRule(recId, attributeType,totalAttributes,j)==1)
               {
                   Rules[j]=1;break;
               }
           }
        }
        else //if the record has missing value/s
        {
           int []leafX= new int[1];
           leafX[0]=recId;
           for(int i=0;i<noOfTree;i++)
           {
           int flg=0;
           for(int j=leafStart[i];j<leafStart[i]+leafLength[i];j++)
           {
               if(isThisRecSatisfyRule(recId, attributeType,totalAttributes,j)==1)
               {
                   Rules[j]=1;
                   flg++;break;
               }
           }
            if(flg==0)
            {
                double mindist=Double.NEGATIVE_INFINITY;
                int rIndex=-1;

                for(int j=leafStart[i];j<leafStart[i]+leafLength[i];j++)
                {
                    int []leafY=toIntArray(LeafRecords[j]);
                    double cudist= distBetweenTwoLeaves(leafX,leafY);
                    double correl=findCorrelationt(recId,j);
                    double wS=(1-cudist)*distWeight+correl*correlWeight;
                    if(wS>mindist)
                    {
                        mindist=wS;rIndex=j;
                    }

                }
                if(rIndex>-1)
                    Rules[rIndex]=1;
            }
            }
        }
    }

/**
 * Find correlation of an intersection which is the merged of two intersections.
 */
private double findCorrelationForBestRule(int curIndex, int mrd)
{

    Set mergedLeaves=new TreeSet<Integer>();
    Set misRec=new TreeSet<Integer>();
    misRec.add(mrd);
    mergedLeaves=union(LeafRecords[curIndex],misRec);
    int []array=toIntArray(mergedLeaves);
    int tmpSize=array.length;
    double [][]dataNum=new double[tmpSize][totalNumericalAttributes];
    String str="";
    for(int i=0;i<tmpSize;i++)
    {
        for(int j=0; j<totalNumericalAttributes; j++)
        {
            str=dataset[array[i]][listOfNumericalAttr[j]];
            if(isMissing(str)==1)
                dataNum[i][j]=0.0;
            else
                dataNum[i][j]=Double.parseDouble(str);
         }
    }

    //find correlation
    double [][]corNum=new double[totalNumericalAttributes][totalNumericalAttributes];
    MatrixCalculation mxCal=new MatrixCalculation();
    mxCal.computeCorrelation(dataNum,tmpSize, totalNumericalAttributes, corNum);
    return calNorm2(corNum,totalNumericalAttributes);
}

/*
 * Similarity between two leaves using Euclidean distance
 */
private double distBetweenTwoLeaves(int []LeafX, int []LeafY)
{
    double ret=0.0, dist=0.0;
    int N=0;
    for(int i=0;i<LeafX.length;i++)
    {
        for(int j=0; j<LeafY.length; j++)
        {
            dist+=distanceXY(LeafX[i],LeafY[j]);
            N++;
         }
    }
    //find avg dist
    if(N>0) ret=dist/(N*totalAttributes);
    return ret;
}

/*
 * Similarity between two records using Euclidean distance
 */

private double distanceXY(int X, int Y)
   {
    double ret=0.0, dist=0.0;
    String xStr,yStr;
    double xV, yV;
    int k=0;
    for(int j=0; j<totalAttributes; j++)
    {
        xStr=datasetNormalized[X][j];
        yStr=datasetNormalized[Y][j];
        if(isMissing(xStr)==0 && isMissing(yStr)==0)
        {
            if(attributeType[j]==1)
            {
                    xV=Double.parseDouble(xStr);
                    yV=Double.parseDouble(yStr);
                    dist+=Math.pow(xV-yV, 2);
            }
            else
            {
                dist+=1.0- similarityXY(k,xStr,yStr);
                k++;

            }
        }
     }
    ret=Math.sqrt(dist);
    return ret;
}

/*
 * Similarity between two records using Euclidean distance
 */

private double similarityXY(int aPos, String xStr, String yStr)
   {
        double ret=0.0;
        int xPos=0,yPos=0;
        for(int i=start1[aPos]; i<start1[aPos]+domainsize1[aPos];i++)
        {
            if(domainValue1[i].equals(xStr))
            {
              xPos=i;
            }
            if(domainValue1[i].equals(yStr))
            {
              yPos=i;
            }
        }
        ret=S[xPos][yPos];
        return ret;

}


    /*
     * return attr info
     */
    private void getAttrType(String atttrInfo)
    {
        FileManager fileManager = new FileManager();
        StringTokenizer tokenizer;
        String [] aFile = fileManager.readFileAsArray(new File(atttrInfo));
        tokenizer = new StringTokenizer(aFile[0], " ,\t\n\r\f");
        totalAttributes=tokenizer.countTokens();
        attributeType=new int[totalAttributes];
        attrSType=new String[totalAttributes];
        totalNumericalAttributes=0;
       
        for(int i=0;i<totalAttributes;i++)
        {
            attributeType[i]=Integer.parseInt(tokenizer.nextToken());
            if(attributeType[i]==1)
            {
                totalNumericalAttributes++;
                attrSType[i]="n";
            }
            else
            {
                attrSType[i]="c";
            }
        }

        listOfNumericalAttr=new int[totalNumericalAttributes];
        int j=0;
        for(int i=0;i<totalAttributes;i++)
        {
            if(attributeType[i]==1)
            {
                listOfNumericalAttr[j]=i;j++;
            }
        }
        
    }
  
 /*
     * If there is no leaf then assign all records into a leaf
     */
    private void ifNoTree(String dataFile)
    {
        totalLeaf=1;
        LeafRecords=new TreeSet[totalLeaf];
        for(int j=0;j<totalLeaf;j++)
            LeafRecords[j]=new TreeSet<Integer>();
        FileManager fileManager = new FileManager();
        String []dataArray=fileManager.readFileAsArray(new File(dataFile));
        for(int i=0; i<dataArray.length;i++)
        {
            LeafRecords[0].add(i);
        }
    }


/**
 * this will check whether a record satisfy a rule
 */
private int isThisRecSatisfyRule(int RecId, int []attrType, int noAttr, int ruleId)
    {
        int flag=0;
        String dStr,rStr;
        int match=0, condition=0;
        StringTokenizer tokenizerRule= new StringTokenizer(logicRuleForEachLeaf[ruleId], " \t\n\r\f");
        for(int i=0;i<noAttr;i++)
        {
            dStr=dataset[RecId][i];
            rStr=tokenizerRule.nextToken();
            if(!rStr.equals("-")&&attrType[i]!=2)
            {
                condition++;
                if(isMissing(dStr)==0)
                {
                if(attrType[i]==0)   //for categorical
                {
                    if(rStr.equals(dStr))
                    {
                        match++;
                    }

                }
                else if(attrType[i]==1)  //for numerical
                {
                  double dVal=Double.parseDouble(dStr);
                  String dh;
                  dh=rStr.substring(1, rStr.length());
                  if(rStr.startsWith("G"))
                  {
                      double drul=Double.parseDouble(dh);
                      if(dVal>drul)match++;

                  }
                  else if(rStr.startsWith("L"))
                  {
                      double drul=Double.parseDouble(dh);
                      if(dVal<=drul)match++;
                  }
                  else if(rStr.startsWith("R"))
                  {
                      int indexOfComma = dh.lastIndexOf(",");
                      double leftDh=Double.parseDouble(dh.substring(0, indexOfComma-1));
                      double rightDh=Double.parseDouble(dh.substring(indexOfComma+1, dh.length()));


                      if(leftDh==rightDh)
                      {
                            if(dVal==rightDh)match++;
                      }
                     else if(leftDh<rightDh)
                      {
                          if(dVal>leftDh && dVal<=rightDh)match++;
                     }
                      else
                      {
                           if(dVal>=rightDh && dVal<=leftDh)match++;
                      }

                  }
                }
                }
                else
                {
                    String cv=dataset[RecId][noAttr-1];
                    if(isMissing(cv)==0 && cv.equals(majorityClassValueOfEachLeaf[ruleId]))
                    {
                        match++;
                    }
                    
                }
            }
        }
       if(match==condition) flag=1;   //record satisfied the rule
       return flag;
    }

/**
  * this function will indicate whether or not a records has missing value.
  *
  * @param recId the id of a record which will be checked for missing/non-missing
  * @return ret an integer value 0->No missing, 1->Missing
  */

 private int isRecMissing(int recId)
    {
        int ret=0;
        for(int i=0;i<totalAttributes;i++)
            if(isMissing(dataset[recId][i])==1)
            {
                ret=1;break;
            }

      return ret;
    }
 
 /**
 * this will find the majority class value of a leaf
 *
 * @param attrType the integer array of attributes types
 * @param noAttr the integer value indicating number of attributes
 * @param rule the rule of a leaf
 * @return cv the majority class value of a given rule
 */
private String findMajorityClassValues(int []attrType, int noAttr, String rule)
    {
        String rStr,cv="?";
        String data="";
        StringTokenizer tokenizerRule= new StringTokenizer(rule, " \t\n\r\f");
        for(int i=0;i<noAttr;i++)
        {
            rStr=tokenizerRule.nextToken();
            if(!rStr.equals("-")&&attrType[i]==2)
            {
               data= rStr;
               break;
            }
        }
        if(!data.equals(""))
        {
            int max=0,cmax=0;
            String cVal="",temp="";
            StringTokenizer tokenizerData = new StringTokenizer(data, " {};,\t\n\r\f");
            int cnt=tokenizerData.countTokens();
            for(int i=0;i<cnt;i++)
            {
                cVal=tokenizerData.nextToken();
                i++;
                temp=tokenizerData.nextToken();
                cmax=Integer.parseInt(temp);
                if(cmax>max)
                {
                   max=cmax;
                   cv=cVal;
                }
            }
        }
       return cv;
    }

 /**
  * this function will indicate whether or not a value is missing.
  * 
  * @param oStr the string to be checked
  * @return ret an integer value 0->No missing, 1->Missing
  */

 private int isMissing(String oStr)
    {
       int ret=0;
       if(oStr.equals("")||oStr.equals("?")||oStr.equals("�"))
                     {
                         ret=1;
                    }
       return ret;
    }
 

}
