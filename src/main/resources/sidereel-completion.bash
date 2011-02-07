#!/bin/bash

_sidereel() {
  COMPREPLY=()
  if [ $COMP_CWORD -eq 1 ]; then
    cur="${COMP_WORDS[COMP_CWORD]}"
    opts=`list-shows.py`
    COMPREPLY=($(compgen -W "$opts" -- "$cur"))
  fi
}

complete -o default -F _sidereel sidereel
