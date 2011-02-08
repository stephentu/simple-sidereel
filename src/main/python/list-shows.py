#!/usr/bin/python
import os
import sys
import time
import urllib

from BeautifulSoup import BeautifulSoup

_CACHE_FILE='.sidereel-show-list'
_CACHE_AGE=7*24*60*60 # 1 week (7 days) in seconds

def user_home():
  # hack?
  return os.path.expanduser('~')

def user_cache_file():
  return user_home() + os.sep + _CACHE_FILE

# script invocation
if __name__ == "__main__":
  # check local user cache
  cf = user_cache_file()
  if os.path.isfile(cf):
    st = os.stat(cf)
    if time.time() - st.st_mtime <= _CACHE_AGE:
      # read from cache file instead
      cfp = open(cf, 'r')
      sys.stdout.write(cfp.read()) # TODO: buffer reads
      sys.exit(0)

  # read results from web and update cache file
  cfp = open(cf, 'w')

  # fetch from sidereel
  f = urllib.urlopen("http://www.sidereel.com/_television")
  s = f.read()
  soup = BeautifulSoup(s)
  for div in soup.findAll('div', 'show-section'):
    for a in div.findAll('a'):
      name = a['href'][1:]
      print name
      cfp.write(name + "\n")

  cfp.close()
