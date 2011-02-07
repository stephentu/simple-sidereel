Simple Sidereel 
===============
This is a very primitive program which is designed to allow you to get a list of all URLs for a particular episode of a show on [Sidereel](http://www.sidereel.com), without having to actually go to their website. It simply scrapes HTML from their site, so it is quite susceptible to breakage when their layout changes.

JSoup is the only dependency. However, for bash completion, [BeautifulSoup](http://www.crummy.com/software/BeautifulSoup/) is a necessary dependency (included in this distribution).

Installation
============
Simply extract the contents of [simple-sidereel.tar.gz](https://github.com/downloads/stephentu/simple-sidereel/simple-sidereel.tar.gz) into a directory which is on your path. Then make sure `sidereel` and `list-shows.py` are executable.

Compiling from Source
=====================
If you want to compile from sources, simply run `mvn compile` in the top level directory. Run `mvn assembly:assembly` to build a jar with all dependencies.

Usage
=====
There are three modes of operation. The first mode is to list a summary of all episodes from all seasons for a particular show:
    sidereel Show_Name

To get a summary of all episodes for a particular season:
    sidereel Show_Name Season_Number

To get all links for a particular episode:
    sidereel Show_Name Season_Number Episode_Number

Bash Completion
===============
To enable bash completion for the `Show_Name`, simply source `sidereel-completion.bash`. For this to work, `list-shows.py` must be executable.
