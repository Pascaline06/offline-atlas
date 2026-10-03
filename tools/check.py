"""Run host-side behavioral checks without an Android SDK or javac launcher."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
subprocess.run(['python3','-m','unittest','discover','-s','tests','-v'],cwd=ROOT,check=True)
with tempfile.TemporaryDirectory() as directory:
    source=ROOT/'app/src/main/java/org/offlineatlas'
    modules=['AnswerReview','ComparisonQuery','WikiText','ResearchEvidence','EvidenceFallback',
             'AnswerCompleteness','SourceWindows','VoyageListings','RetrievalPlan','QueryPolicy','AssetBudget','AssetSwap']
    cases=sorted((ROOT/'tests').glob('*Cases.java'))
    subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',directory,
                    *map(str,[source/(name+'.java') for name in modules]),*map(str,cases)],check=True)
    for case in cases:
        subprocess.run(['java','-cp',directory,'org.offlineatlas.'+case.stem],check=True)
