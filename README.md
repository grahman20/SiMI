# SiMI
SiMI imputes numerical and categorical missing values by making an educated guess based on records that are similar to the record having a missing value. Using the similarity and correlations, missing values are then imputed. To achieve a higher quality of imputation some segments are merged together using a novel approach.

# Reference

Rahman, M. G. and Islam, M. Z. (2013): Missing Value Imputation Using Decision Trees and Decision Forests by Splitting and Merging Records: Two Novel Techniques. Knowledge-Based Systems, Vol. 53, pp. 51 - 65, ISSN 0950-7051, DOI information: http://dx.doi.org/10.1016/j.knosys.2013.08.023. 

## BibTeX
```
@article{rahman2013imputation,
  title={Missing Value Imputation Using Decision Trees and Decision Forests by Splitting and Merging Records: Two Novel Techniques},
  author={Rahman, Md. Geaur and Islam, Md Zahidul},
  journal={Knowledge-Based Systems},
  volume={53},
  pages={51--65},
  year={2013},
  publisher={Elsevier}
}
```

@author Md Geaur Rahman <https://csusap.csu.edu.au/~grahman/>
  
# Two folders:
 
 1. SiMI_project (NetBeans project)
 2. SampleData 
 
 SiMI is developed based on Java programming language (jdk1.8.0_211) using NetBeans IDE (8.0.2). 
 
# How to run:
 
	1. Open project in NetBeans
	2. Run the project

# Sample input and output:
run:
Please enter the name of the file containing the 2 line attribute information.(example: c:\data\attrinfo.txt)

C:\SampleData\attrinfo.txt

Please enter the name of the data file having missing values: (example: c:\data\data.txt)

C:\SampleData\data.txt

Please enter the name of the output file: (example: c:\data\out.txt)

C:\SampleData\output.txt


Imputation by SiMi is done. The completed data set is written to: 
C:\SampleData\output.txt
