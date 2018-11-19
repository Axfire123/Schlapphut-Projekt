﻿using System;
using System.IO;
using System.Xml;
using NUnit.Framework;
using SharpKml.Base;
using SharpKml.Dom;

namespace UnitTests.Base
{
    [TestFixture]
    public class SerializerTest
    {
        private const string XmlNamespace = "http://www.example.com";
        private const string ChildElementName = "ChildElementS";
        private const string TestElementName = "TestElementS";

        public class ChildElement : Element
        {
            [KmlElement("counter")]
            public int Counter { get; set; }
        }

        public class TestElement : Element
        {
            private ChildElement child;

            [KmlAttribute("Attribute")]
            public string Attribute { get; set; }

            [KmlAttribute("EnumAtt")]
            public ColorMode? EnumAtt { get; set; }

            // Also test that NamespaceUri doesn't matter if its string.Empty or null
            [KmlElement("Enum", "")]
            public ColorMode Enum { get; set; }

            [KmlElement("Int", null)]
            public int Int { get; set; }

            [KmlElement("OptionalInt", null)]
            public int? OptionalInt { get; set; }

            [KmlElement("WebAddress", null)]
            public Uri Uri { get; set; }

            [KmlElement(null)]
            public ChildElement Child
            {
                get => this.child;
                set => this.UpdatePropertyChild(value, ref this.child);
            }
        }

        static SerializerTest()
        {
            KmlFactory.Register<ChildElement>(new XmlComponent(null, ChildElementName, XmlNamespace));
            KmlFactory.Register<TestElement>(new XmlComponent(null, TestElementName, XmlNamespace));
        }

        [Test]
        public void TestAttributes()
        {
            // Try the attributes first
            var element = new TestElement
            {
                Attribute = "attribute",
                EnumAtt = ColorMode.Random
            };

            var serializer = new Serializer();
            serializer.Serialize(element);
            Assert.True(FindNode(serializer.Xml, TestElementName, r =>
                {
                    Assert.That(r.GetAttribute("Attribute"), Is.EqualTo("attribute"));
                    Assert.That(r.GetAttribute("EnumAtt"), Is.EqualTo("random"));
                }));

            // Try optional elements = make sure they're only serialized if they have a value
            element.Int = 42;
            serializer.Serialize(element);
            Assert.True(FindNode(serializer.Xml, "Int", r =>
                Assert.That(r.ReadElementContentAsInt(), Is.EqualTo(42))));

            Assert.False(FindNode(serializer.Xml, "OptionalInt", null));

            element.OptionalInt = 0;
            serializer.Serialize(element);
            Assert.True(FindNode(serializer.Xml, "OptionalInt", r =>
                Assert.That(r.ReadElementContentAsInt(), Is.EqualTo(0))));
        }

        [Test]
        public void TestChild()
        {
            var element = new TestElement
            {
                Child = new ChildElement
                {
                    Counter = 1
                }
            };

            var serializer = new Serializer();
            serializer.Serialize(element);

            Assert.True(FindNode(serializer.Xml, "counter", r =>
                Assert.That(r.ReadElementContentAsInt(), Is.EqualTo(1))));
        }

        [Test]
        public void TestFindNode()
        {
            // Make sure the test methods are reporting correct results...
            const string Xml = "<?xml version='1.0'?><root><empty/><child attribute='text'>value</child></root>";

            string value = null;
            Assert.True(FindNode(Xml, "child", r => value = r.ReadElementContentAsString()));
            Assert.That(value, Is.EqualTo("value"));

            Assert.True(FindNode(Xml, "empty", null));

            Assert.False(FindNode(Xml, "invalid", null));
        }

        [Test]
        public void TestCData()
        {
            var balloon = new BalloonStyle
            {
                Text = "<![CDATA[$[description]]]>"
            };

            var serializer = new Serializer();
            serializer.SerializeRaw(balloon);

            string expected =
                "<BalloonStyle xmlns=\"http://www.opengis.net/kml/2.2\">" +
                "<text>" + balloon.Text + "</text>" +
                "</BalloonStyle>";

            Assert.That(serializer.Xml, Is.EqualTo(expected));
        }

        [Test]
        public void SerializeShouldCheckForNullArguments()
        {
            var serializer = new Serializer();

            Assert.That(() => serializer.Serialize(null),
                Throws.InstanceOf<ArgumentNullException>());

            Assert.That(() => serializer.Serialize(null, new MemoryStream()),
                Throws.InstanceOf<ArgumentNullException>());

            Assert.That(() => serializer.Serialize(new Kml(), null),
                Throws.InstanceOf<ArgumentNullException>());
        }

        [Test]
        public void SerializeShouldNotPrefixElementsIfTheyAreInTheDefaultNamespace()
        {
            var element = new TestElement();
            element.AddAttribute(new XmlComponent("xmlns", "p", "") { Value = XmlNamespace });
            element.Child = new ChildElement();

            var serializer = new Serializer();
            serializer.SerializeRaw(element);

            Assert.That(serializer.Xml, Contains.Substring("<" + ChildElementName + ">"));
        }

        private static bool FindNode(string xml, string name, Action<XmlReader> callback)
        {
            using (var stringReader = new StringReader(xml))
            using (var reader = XmlReader.Create(stringReader))
            {
                while (reader.Read())
                {
                    if ((reader.NodeType == XmlNodeType.Element) &&
                        (reader.LocalName == name))
                    {
                        callback?.Invoke(reader);
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
