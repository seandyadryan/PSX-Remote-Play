# Generated protocol definitions

`takion.pb.c` and `takion.pb.h` are generated from `lib/protobuf/takion.proto`
in pinned Chiaki-ng commit `ca5379f4504af6e63cf5d32b5626de2425b2382e`.
The schema is covered by the upstream AGPL license/exception; the Nanopb runtime
and generator have their own zlib license. Full texts are bundled under assets/licenses.

Generation used the pinned Nanopb 0.4.6 generator from dependencies.json,
grpcio-tools 1.71.2, protobuf 5.29.5 and setuptools 75.8.2. These Python tools
are needed only to regenerate the checked-in C definitions, not to build the APK.

After bootstrapping source dependencies, with those Python packages installed:

```powershell
python -m grpc_tools.protoc -I app/build/native-provider/chiaki-ng-ca5379f4504af6e63cf5d32b5626de2425b2382e/lib/protobuf --descriptor_set_out=app/build/native-provider/takion.pb app/build/native-provider/chiaki-ng-ca5379f4504af6e63cf5d32b5626de2425b2382e/lib/protobuf/takion.proto
python app/build/native-provider/nanopb-afc499f9a410fc9bbf6c9c48cdd8d8b199d49eb4/generator/nanopb_generator.py -Dapp/src/main/cpp/generated app/build/native-provider/takion.pb
```
