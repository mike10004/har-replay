#!/usr/bin/env python3

from __future__ import print_function

import os
import os.path
import sys
from argparse import ArgumentParser

DELIMITER=':'

def main(args):
  jars = []
  lib_dir = os.path.normpath(args.lib_dir)
  root_dir = os.path.normpath(args.root_dir)
  for root, dirs, files in os.walk(lib_dir):
    for filename in files:
      jar = os.path.join(root, filename)
      if jar.startswith(root_dir):
        jar = jar[len(root_dir):]
      jars.append(jar)
      print("classpath: {}".format(jar))
  classpath_arg = args.delimiter.join(jars)
  outdir = os.path.dirname(args.output_file)
  if not os.path.exists(outdir):
    os.makedirs(outdir)
  with open(args.output_file, 'w') as ofile:
    print(classpath_arg, file=ofile)
  print("wrote {}".format(args.output_file))

if __name__ == '__main__':
  p = ArgumentParser()
  p.add_argument("root_dir")
  p.add_argument("lib_dir")
  p.add_argument("output_file")
  p.add_argument("--delimiter", default=DELIMITER)
  args = p.parse_args()
  exit(main(args))