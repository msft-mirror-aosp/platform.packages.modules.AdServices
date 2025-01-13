# Mobile Data Download Assets

This directory contains asset files used for local testing in the Mobile Data
Download (MDD) component. These files are served locally to eliminate network
dependency and flakiness in tests.

## How it Works

These files are served locally using LocalFileDownloader and TestFileDownloader.
When a network call is made to a specific URL by MDD, the corresponding file
from this directory is served as the response.

For example, if test makes a call to

https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/4503/fecd522d3dcfbe1b3b1f1054947be8528be43e97

The file named `fecd522d3dcfbe1b3b1f1054947be8528be43e97` in this directory will
be served as the response.

## Adding new MDD File Downloads

If you need to add new assets for testing, follow these steps:

1.  **Obtain the file:** Download the corresponding file (from gstatic.com or
    dl.google.com or edgedl.me.gvt1.com).
2.  **Name the file:** The file name should match the last part of the URL that
    will be used to access it.
3.  **Add the file:** Place the file in this directory.
