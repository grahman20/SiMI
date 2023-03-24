/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class Main {
        /** command line reader */
    BufferedReader stdIn;
        /** class name, used in logging errors */
    static String className = simi.Main.class.getName();
    
    public Main()
    {
        stdIn = new BufferedReader(new InputStreamReader(System.in));
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Main terminal=new Main();
        String fileAttrInfo = terminal.inputFileName("Please enter the name of the file containing the 2 line attribute information.(example: c:\\data\\attrinfo.txt?)");
        String fileDataFileIn= terminal.inputFileName("Please enter the name of the data file having missing values: (example: c:\\data\\data.txt?)");
        String fileOutput = terminal.inputFileName("Please enter the name of the output file: (example: c:\\data\\out.txt?)");
        //call SiMI
        double lamda=0.7; 
        SiMI simi=new SiMI();
        simi.runSiMI(fileAttrInfo, fileDataFileIn, fileOutput, lamda);
        System.out.println("\nImputation by SiMI is done. The completed data set is written to: \n"+fileOutput);
    }
      

    /**
     * Given a message to display to the user, ask user to enter a file name.
     *
     * @param message message to user prompting for filename
     * @return filename entered by user
     */
    private String inputFileName(String message)
    {
        String fileName = "";
        try
        {
            System.out.println(message);
            fileName = stdIn.readLine();
        }
        catch (IOException ex)
        {
            Logger.getLogger(className).log(Level.SEVERE, null, ex);
        }
        return fileName;
    }

}
